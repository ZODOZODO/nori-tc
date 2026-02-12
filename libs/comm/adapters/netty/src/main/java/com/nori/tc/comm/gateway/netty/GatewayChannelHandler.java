package com.nori.tc.apps.commgateway.netty;

import com.nori.tc.apps.commgateway.comm.GatewayProcessingService;
import com.nori.tc.apps.commgateway.config.GatewayNettyProperties;
import com.nori.tc.apps.commgateway.metrics.GatewayLogSampler;
import com.nori.tc.apps.commgateway.metrics.GatewayMetrics;
import com.nori.tc.apps.commgateway.metrics.GatewayLogContext;
import com.nori.tc.comm.domain.type.CommInterfaceType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty inbound handler (PASSIVE/ACTIVE 공용).
 *
 * 상태머신 요약
 * - UNBOUND: eqpId 미확정 상태
 *   - channelRead에서 byte[] enqueue만 수행
 *   - bind 시도는 별도 executor에서 수행
 *   - bind 성공 시 BOUND로 전이
 * - BOUND: eqpId 확정 상태
 *   - channelRead에서 inboundQueue enqueue만 수행
 *
 * 성능 원칙
 * - Netty channelRead는 enqueue까지만 수행한다
 * - 파싱/세션/Kafka IO는 worker 스레드에서 처리한다
 */
public final class GatewayChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GatewayChannelHandler.class);

    private final CommInterfaceType interfaceType;
    private final String presetEqpId;
    private final GatewayNettyProperties nettyProperties;
    private final GatewayProcessingService processingService;
    private final EqpBindingService bindingService;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;
    private final HsmsEqpIdExtractor hsmsExtractor;
    private final SocketEqpIdExtractor socketExtractor;
    private final BindAttemptExecutor bindExecutor;

    private final AtomicBoolean bindScheduled = new AtomicBoolean(false);
    private final UnboundInbox unboundInbox;

    public GatewayChannelHandler(
            final CommInterfaceType interfaceType,
            final String presetEqpId,
            final GatewayNettyProperties nettyProperties,
            final GatewayProcessingService processingService,
            final EqpBindingService bindingService,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler,
            final HsmsEqpIdExtractor hsmsExtractor,
            final SocketEqpIdExtractor socketExtractor,
            final BindAttemptExecutor bindExecutor
    ) {
        this.interfaceType = Objects.requireNonNull(interfaceType, "interfaceType is null");
        this.presetEqpId = (presetEqpId == null || presetEqpId.isBlank()) ? null : presetEqpId;
        this.nettyProperties = Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
        this.hsmsExtractor = hsmsExtractor;
        this.socketExtractor = socketExtractor;
        this.bindExecutor = Objects.requireNonNull(bindExecutor, "bindExecutor is null");
        this.unboundInbox = new UnboundInbox(
                nettyProperties.getUnboundBufferInitialBytes(),
                nettyProperties.getUnboundBufferMaxBytes()
        );
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        final Channel channel = ctx.channel();
        NettyChannelAttributes.setBindState(channel, BindState.UNBOUND);
        metrics.incrementActiveConnections();
        metrics.incrementUnboundConnections();

        // ACTIVE: eqpId를 알고 있으므로 즉시 bind
        if (presetEqpId != null) {
            final EqpBindingService.BindResult result = bindingService.bindActive(presetEqpId, interfaceType, channel);
            if (result != EqpBindingService.BindResult.OK) {
                if (result == EqpBindingService.BindResult.DUPLICATE_CONNECTION) {
                    metrics.incrementDuplicateEqpReject();
                    if (logSampler.shouldLogDuplicateReject()) {
                        log.warn("DUPLICATE_EQP_REJECT. eqpId={}, remote={}", presetEqpId, channel.remoteAddress());
                    }
                } else if (result == EqpBindingService.BindResult.NOT_OWNED) {
                    if (logSampler.shouldLogNotOwnerReject()) {
                        log.warn("NOT_OWNER_PARTITION. eqpId={}, remote={}", presetEqpId, channel.remoteAddress());
                    }
                } else {
                    log.warn("Active bind failed. eqpId={}, result={}, remote={}",
                            presetEqpId, result, channel.remoteAddress());
                }
                metrics.decrementUnboundConnections();
                metrics.decrementActiveConnections();
                channel.close();
                return;
            }

            NettyChannelAttributes.setEqpId(channel, presetEqpId);
            NettyChannelAttributes.setBindState(channel, BindState.BOUND);
            metrics.decrementUnboundConnections();
            metrics.incrementBoundConnections();

            // 설비별 로그 파일 생성을 위해 성공 바인딩 로그를 남긴다.
            try (GatewayLogContext ignored = GatewayLogContext.withEqpId(presetEqpId)) {
                log.info("ACTIVE_BIND_OK. eqpId={}, remote={}", presetEqpId, channel.remoteAddress());
            }

            sendInitializeIfNeeded(channel);
            return;
        }

        // PASSIVE: 등록 타임아웃. UNBOUND 상태가 일정 시간 지속되면 종료한다.
        final ScheduledFuture<?> task = channel.eventLoop().schedule(() -> {
            final BindState state = NettyChannelAttributes.getBindState(channel);
            if (state == BindState.UNBOUND) {
                metrics.incrementBindTimeout();
                if (logSampler.shouldLogBindTimeout()) {
                    log.warn("BIND_TIMEOUT. closing channel. remote={}", channel.remoteAddress());
                }
                channel.close();
            }
        }, nettyProperties.getBindTimeoutSeconds(), TimeUnit.SECONDS);

        NettyChannelAttributes.setBindTimeoutTask(channel, task);
        if (log.isDebugEnabled()) {
            log.debug("Bind timeout scheduled. seconds={}, remote={}",
                    nettyProperties.getBindTimeoutSeconds(), channel.remoteAddress());
        }

        sendInitializeIfNeeded(channel);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        final Channel channel = ctx.channel();
        final BindState state = NettyChannelAttributes.getBindState(channel);
        if (state == BindState.BOUND) {
            metrics.decrementBoundConnections();
        } else {
            metrics.decrementUnboundConnections();
        }
        metrics.decrementActiveConnections();
        bindingService.unbind(channel);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            ReferenceCountUtil.release(msg);
            return;
        }

        try {
            // 1) byte[] copy (IO 스레드에서 최소 작업)
            // IO 스레드는 enqueue-only (파싱/세션 작업 금지)
            final byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);

            final Channel channel = ctx.channel();
            final BindState state = NettyChannelAttributes.getBindState(channel);

            if (state == BindState.BOUND) {
                // 2) BOUND: inbound 큐에 enqueue만 수행
                final String eqpId = NettyChannelAttributes.getEqpId(channel);
                if (eqpId != null) {
                    processingService.enqueueInbound(eqpId, bytes);
                }
                return;
            }

            // 3) UNBOUND: unbound inbox에 enqueue만 수행 (파싱은 별도 스레드)
            if (!unboundInbox.offer(bytes)) {
                log.warn("Unbound inbox overflow. closing channel.");
                channel.close();
                return;
            }
            scheduleBindAttempt(channel);

        } finally {
            ReferenceCountUtil.release(buf);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        log.warn("Netty channel error", cause);
        ctx.close();
    }

    private void scheduleBindAttempt(final Channel channel) {
        if (!bindScheduled.compareAndSet(false, true)) {
            return;
        }

        bindExecutor.submit(() -> {
            try {
                attemptBind(channel);
            } finally {
                bindScheduled.set(false);
            }
        });
    }

    private void attemptBind(final Channel channel) {
        if (!channel.isActive()) {
            return;
        }
        if (NettyChannelAttributes.getBindState(channel) == BindState.BOUND) {
            return;
        }

        // Bind flow (UNBOUND):
        // - move queued bytes to buffer
        // - try extract eqpId (registration message)
        // - bind + create mailbox if owned/valid
        final Optional<String> eqpIdOpt;
        try {
            // inbox에 쌓인 바이트를 버퍼로 이동 (IO 스레드 밖에서 처리)
            unboundInbox.drainToBuffer();

            // 등록 메시지 파싱:
            // - 등록 성공 시 eqpId 반환
            // - 등록 메시지가 아니면 버퍼를 유지하고 empty 반환
            eqpIdOpt = switch (interfaceType) {
                case HSMS -> hsmsExtractor.tryExtractEqpId(unboundInbox.buffer());
                case SOCKET -> socketExtractor.tryExtractEqpId(unboundInbox.buffer());
            };
        } catch (Exception ex) {
            log.warn("Bind parsing failed. closing channel.", ex);
            channel.close();
            return;
        }

        if (eqpIdOpt.isEmpty()) {
            return;
        }

        final String eqpId = eqpIdOpt.get();
        final EqpBindingService.BindResult result = bindingService.bindPassive(eqpId, interfaceType, channel);

        if (result != EqpBindingService.BindResult.OK) {
            if (result == EqpBindingService.BindResult.DUPLICATE_CONNECTION) {
                metrics.incrementDuplicateEqpReject();
                if (logSampler.shouldLogDuplicateReject()) {
                    log.warn("DUPLICATE_EQP_REJECT. eqpId={}, remote={}", eqpId, channel.remoteAddress());
                }
            } else if (result == EqpBindingService.BindResult.NOT_OWNED) {
                if (logSampler.shouldLogNotOwnerReject()) {
                    log.warn("NOT_OWNER_PARTITION. eqpId={}, remote={}", eqpId, channel.remoteAddress());
                }
            } else {
                log.warn("Passive bind rejected. eqpId={}, result={}, remote={}", eqpId, result, channel.remoteAddress());
            }
            channel.close();
            return;
        }

        NettyChannelAttributes.setEqpId(channel, eqpId);
        NettyChannelAttributes.setBindState(channel, BindState.BOUND);
        cancelBindTimeout(channel);
        metrics.decrementUnboundConnections();
        metrics.incrementBoundConnections();

        // 설비별 로그 파일 생성을 위해 성공 바인딩 로그를 남긴다.
        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
            log.info("PASSIVE_BIND_OK. eqpId={}, remote={}", eqpId, channel.remoteAddress());
        }


        // 등록 완료 후 남은 메시지 drop: unbound buffer 비움
        unboundInbox.clear();
    }

    private void cancelBindTimeout(final Channel channel) {
        final ScheduledFuture<?> task = NettyChannelAttributes.getBindTimeoutTask(channel);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void sendInitializeIfNeeded(final Channel channel) {
        if (interfaceType != CommInterfaceType.SOCKET) {
            return;
        }
        if (!nettyProperties.isSocketSendInitializeOnConnect()) {
            return;
        }

        final byte[] cmd = socketExtractor.initializeCommandBytes();
        channel.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(cmd));
    }
}
