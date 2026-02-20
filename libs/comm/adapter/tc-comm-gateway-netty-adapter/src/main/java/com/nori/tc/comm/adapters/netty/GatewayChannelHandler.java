package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.config.GatewayNettyProperties;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
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
 * Netty 인바운드 채널 핸들러입니다.
 *
 * <p>ACTIVE/ PASSIVE 채널 경로를 공통 처리하며, 바인딩 전후 상태를 다음처럼 관리합니다.</p>
 * <p>1) UNBOUND: eqpId 미확정 상태(핸드셰이크/등록 대기)</p>
 * <p>2) BOUND: eqpId 확정 상태(메일박스 enqueue 가능)</p>
 *
 * <p>성능 원칙:</p>
 * <p>- channelRead에서는 byte[] 복사 + enqueue까지만 수행합니다.</p>
 * <p>- 파싱/검증/실처리는 worker/바인딩 executor에서 수행합니다.</p>
 */
public final class GatewayChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GatewayChannelHandler.class);

    private final CommInterfaceType interfaceType;
    /**
     * 아웃바운드 채널(게이트웨이 발신)인 경우 미리 알고 있는 대상 eqpId입니다.
     *
     * <p>null이면 서버 수신 채널(게이트웨이 수신)로 동작합니다.</p>
     */
    private final String presetEqpId;
    private final GatewayNettyProperties nettyProperties;
    private final GatewayProcessingService processingService;
    private final EqpBindingService bindingService;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;
    private final HsmsEqpIdExtractor hsmsExtractor;
    private final SocketEqpIdExtractor socketExtractor;
    private final BindAttemptExecutor bindExecutor;

    /**
     * 동일 채널에서 동시 바인딩 시도가 중첩되지 않도록 보호합니다.
     */
    private final AtomicBoolean bindScheduled = new AtomicBoolean(false);
    /**
     * UNBOUND 상태에서 수신된 raw bytes를 임시 보관하는 inbox입니다.
     */
    private final UnboundInbox unboundInbox;

    /**
     * 채널 핸들러를 생성합니다.
     *
     * @param interfaceType 통신 인터페이스 타입
     * @param presetEqpId 아웃바운드 대상 eqpId(수신 채널이면 null)
     * @param nettyProperties netty 런타임 설정
     * @param processingService inbound/outbound enqueue 진입점
     * @param bindingService 설비 바인딩 서비스
     * @param metrics 게이트웨이 메트릭 수집기
     * @param logSampler 로그 샘플링 정책
     * @param hsmsExtractor HSMS eqpId 추출기
     * @param socketExtractor SOCKET eqpId 추출기
     * @param bindExecutor 바인딩 전용 executor
     */
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

    /**
     * 채널 활성화 시 초기 상태를 UNBOUND로 세팅하고 바인딩 준비를 시작합니다.
     *
     * <p>동작 정책:</p>
     * <p>1) 아웃바운드 HSMS는 preset eqpId를 즉시 바인딩합니다(기존 정책 유지).</p>
     * <p>2) SOCKET(inbound/outbound)은 모두 initialize 핸드셰이크를 수행합니다.</p>
     * <p>3) 핸드셰이크/등록 타임아웃을 예약합니다.</p>
     *
     * @param ctx Netty 핸들러 컨텍스트
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        final Channel channel = ctx.channel();
        NettyChannelAttributes.setBindState(channel, BindState.UNBOUND);
        metrics.incrementActiveConnections();
        metrics.incrementUnboundConnections();

        // HSMS 아웃바운드는 현재 설계에서 preset eqpId 즉시 바인딩으로 유지합니다.
        if (shouldBindImmediatelyOnChannelActive()) {
            bindImmediatelyWithPresetEqpId(channel);
            return;
        }

        // SOCKET(inbound/outbound) + HSMS inbound는 UNBOUND 타임아웃 감시를 공통 적용합니다.
        scheduleBindTimeout(channel);
        sendInitializeIfNeeded(channel);
    }

    /**
     * 채널 비활성화 시 메트릭/바인딩 상태를 정리합니다.
     *
     * @param ctx Netty 핸들러 컨텍스트
     */
    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        final Channel channel = ctx.channel();
        cancelBindTimeout(channel);

        final BindState state = NettyChannelAttributes.getBindState(channel);
        if (state == BindState.BOUND) {
            metrics.decrementBoundConnections();
        } else {
            metrics.decrementUnboundConnections();
        }
        metrics.decrementActiveConnections();
        bindingService.unbind(channel);
    }

    /**
     * 수신 메시지를 바인딩 상태에 따라 처리합니다.
     *
     * <p>BOUND 상태면 eqp mailbox로 inbound enqueue하고,
     * UNBOUND 상태면 unbound inbox에 쌓은 뒤 바인딩 시도를 예약합니다.</p>
     *
     * @param ctx Netty 핸들러 컨텍스트
     * @param msg 수신 객체(ByteBuf 기대)
     */
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            ReferenceCountUtil.release(msg);
            return;
        }

        try {
            // IO 스레드에서는 최소 작업(복사 + enqueue)만 수행합니다.
            final byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);

            final Channel channel = ctx.channel();
            final BindState state = NettyChannelAttributes.getBindState(channel);

            if (state == BindState.BOUND) {
                final String eqpId = NettyChannelAttributes.getEqpId(channel);
                if (eqpId != null) {
                    processingService.enqueueInbound(eqpId, bytes);
                }
                return;
            }

            if (!unboundInbox.offer(bytes)) {
                log.warn("Unbound inbox overflow. closing channel. remote={}", channel.remoteAddress());
                channel.close();
                return;
            }
            scheduleBindAttempt(channel);

        } finally {
            ReferenceCountUtil.release(buf);
        }
    }

    /**
     * 채널 예외 발생 시 경고 로그를 남기고 채널을 닫습니다.
     *
     * @param ctx Netty 핸들러 컨텍스트
     * @param cause 예외
     */
    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        log.warn("Netty channel error", cause);
        ctx.close();
    }

    /**
     * 채널 활성화 직후 즉시 바인딩할지 여부를 판단합니다.
     *
     * <p>현재 정책:</p>
     * <p>- preset eqpId가 있고, 인터페이스가 SOCKET이 아닌 경우(즉, HSMS 아웃바운드) 즉시 바인딩</p>
     * <p>- SOCKET은 inbound/outbound 모두 핸드셰이크 후 바인딩</p>
     *
     * @return 즉시 바인딩 여부
     */
    private boolean shouldBindImmediatelyOnChannelActive() {
        return presetEqpId != null && interfaceType != CommInterfaceType.SOCKET;
    }

    /**
     * preset eqpId 기반 즉시 바인딩을 수행합니다.
     *
     * <p>즉시 바인딩 실패 시 채널을 종료하고, 성공 시 BOUND 상태로 전환합니다.</p>
     *
     * @param channel 대상 채널
     */
    private void bindImmediatelyWithPresetEqpId(final Channel channel) {
        final EqpBindingService.BindResult result = bindingService.bindActive(presetEqpId, interfaceType, channel);
        if (result != EqpBindingService.BindResult.OK) {
            logBindRejected(result, presetEqpId, channel, "Immediate outbound");
            metrics.decrementUnboundConnections();
            metrics.decrementActiveConnections();
            channel.close();
            return;
        }

        NettyChannelAttributes.setEqpId(channel, presetEqpId);
        NettyChannelAttributes.setBindState(channel, BindState.BOUND);
        cancelBindTimeout(channel);
        metrics.decrementUnboundConnections();
        metrics.incrementBoundConnections();

        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(presetEqpId)) {
            log.info("ACTIVE_BIND_OK. eqpId={}, remote={}", presetEqpId, channel.remoteAddress());
        }

        // HSMS 즉시 바인딩 경로에서는 no-op이지만, 구조 일관성을 위해 호출합니다.
        sendInitializeIfNeeded(channel);
    }

    /**
     * 바인딩 시도 작업을 executor에 예약합니다.
     *
     * <p>동일 채널에서 동시 예약은 1건만 허용합니다.</p>
     *
     * @param channel 대상 채널
     */
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

    /**
     * UNBOUND 채널의 바인딩 시도를 수행합니다.
     *
     * <p>SOCKET outbound의 경우 추가 검증으로 preset eqpId와 rep eqpId 일치 여부를 확인합니다.</p>
     *
     * @param channel 대상 채널
     */
    private void attemptBind(final Channel channel) {
        if (!channel.isActive()) {
            return;
        }
        if (NettyChannelAttributes.getBindState(channel) == BindState.BOUND) {
            return;
        }

        final Optional<String> extractedEqpIdOpt;
        try {
            // 별도 executor에서 unbound inbox를 버퍼로 드레인하고 프레임 파싱을 수행합니다.
            unboundInbox.drainToBuffer();
            extractedEqpIdOpt = switch (interfaceType) {
                case HSMS -> hsmsExtractor.tryExtractEqpId(unboundInbox.buffer());
                case SOCKET -> socketExtractor.tryExtractEqpId(unboundInbox.buffer());
            };
        } catch (Exception ex) {
            log.warn("Bind parsing failed. closing channel. remote={}", channel.remoteAddress(), ex);
            channel.close();
            return;
        }

        if (extractedEqpIdOpt.isEmpty()) {
            return;
        }

        final String extractedEqpId = extractedEqpIdOpt.get();
        final boolean outboundChannel = presetEqpId != null;

        // SOCKET 아웃바운드는 핸드셰이크 응답 eqpId가 목표 설비와 같아야만 바인딩합니다.
        if (outboundChannel
                && interfaceType == CommInterfaceType.SOCKET
                && !presetEqpId.equals(extractedEqpId)) {
            log.warn("SOCKET_INITIALIZE_EQPID_MISMATCH. expectedEqpId={}, replyEqpId={}, remote={}",
                    presetEqpId,
                    extractedEqpId,
                    channel.remoteAddress());
            channel.close();
            return;
        }

        final String bindEqpId = outboundChannel ? presetEqpId : extractedEqpId;
        final EqpBindingService.BindResult result = outboundChannel
                ? bindingService.bindActive(bindEqpId, interfaceType, channel)
                : bindingService.bindPassive(bindEqpId, interfaceType, channel);

        if (result != EqpBindingService.BindResult.OK) {
            logBindRejected(result, bindEqpId, channel, outboundChannel ? "Outbound" : "Inbound");
            channel.close();
            return;
        }

        NettyChannelAttributes.setEqpId(channel, bindEqpId);
        NettyChannelAttributes.setBindState(channel, BindState.BOUND);
        cancelBindTimeout(channel);
        metrics.decrementUnboundConnections();
        metrics.incrementBoundConnections();

        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(bindEqpId)) {
            if (outboundChannel) {
                log.info("ACTIVE_BIND_OK. eqpId={}, remote={}", bindEqpId, channel.remoteAddress());
            } else {
                log.info("PASSIVE_BIND_OK. eqpId={}, remote={}", bindEqpId, channel.remoteAddress());
            }
        }

        // 등록 완료 후 UNBOUND 버퍼를 비웁니다.
        unboundInbox.clear();
    }

    /**
     * 바인딩 거절 결과를 코드별로 로깅합니다.
     *
     * @param result 바인딩 결과
     * @param eqpId 대상 eqpId
     * @param channel 대상 채널
     * @param bindPath 로깅용 바인딩 경로 명
     */
    private void logBindRejected(
            final EqpBindingService.BindResult result,
            final String eqpId,
            final Channel channel,
            final String bindPath
    ) {
        if (result == EqpBindingService.BindResult.DUPLICATE_CONNECTION) {
            metrics.incrementDuplicateEqpReject();
            if (logSampler.shouldLogDuplicateReject()) {
                log.warn("DUPLICATE_EQP_REJECT. bindPath={}, eqpId={}, remote={}",
                        bindPath,
                        eqpId,
                        channel.remoteAddress());
            }
            return;
        }

        if (result == EqpBindingService.BindResult.NOT_OWNED) {
            if (logSampler.shouldLogNotOwnerReject()) {
                log.warn("NOT_OWNER_PARTITION. bindPath={}, eqpId={}, remote={}",
                        bindPath,
                        eqpId,
                        channel.remoteAddress());
            }
            return;
        }

        log.warn("Bind rejected. bindPath={}, eqpId={}, result={}, remote={}",
                bindPath,
                eqpId,
                result,
                channel.remoteAddress());
    }

    /**
     * UNBOUND 타임아웃 감시 작업을 채널 이벤트 루프에 예약합니다.
     *
     * <p>타임아웃 내에 BOUND 전환이 일어나지 않으면 채널을 닫습니다.</p>
     *
     * @param channel 대상 채널
     */
    private void scheduleBindTimeout(final Channel channel) {
        cancelBindTimeout(channel);

        final ScheduledFuture<?> task = channel.eventLoop().schedule(() -> {
            if (!channel.isActive()) {
                return;
            }

            final BindState state = NettyChannelAttributes.getBindState(channel);
            if (state == BindState.UNBOUND) {
                metrics.incrementBindTimeout();
                if (logSampler.shouldLogBindTimeout()) {
                    log.warn("BIND_TIMEOUT. closing channel. seconds={}, interfaceType={}, presetEqpId={}, remote={}",
                            nettyProperties.getBindTimeoutSeconds(),
                            interfaceType,
                            presetEqpId,
                            channel.remoteAddress());
                }
                channel.close();
            }
        }, nettyProperties.getBindTimeoutSeconds(), TimeUnit.SECONDS);

        NettyChannelAttributes.setBindTimeoutTask(channel, task);
        if (log.isDebugEnabled()) {
            log.debug("Bind timeout scheduled. seconds={}, interfaceType={}, presetEqpId={}, remote={}",
                    nettyProperties.getBindTimeoutSeconds(),
                    interfaceType,
                    presetEqpId,
                    channel.remoteAddress());
        }
    }

    /**
     * 예약된 바인딩 타임아웃 작업을 취소합니다.
     *
     * @param channel 대상 채널
     */
    private void cancelBindTimeout(final Channel channel) {
        final ScheduledFuture<?> task = NettyChannelAttributes.getBindTimeoutTask(channel);
        if (task != null) {
            task.cancel(false);
        }
        NettyChannelAttributes.setBindTimeoutTask(channel, null);
    }

    /**
     * SOCKET 채널일 때 initialize 명령을 전송합니다.
     *
     * <p>설정값이 비활성화면 전송하지 않습니다.</p>
     *
     * @param channel 대상 채널
     */
    private void sendInitializeIfNeeded(final Channel channel) {
        if (interfaceType != CommInterfaceType.SOCKET) {
            return;
        }
        if (!nettyProperties.isSocketSendInitializeOnConnect()) {
            return;
        }

        final byte[] cmd = socketExtractor.initializeCommandBytes();
        if (log.isDebugEnabled()) {
            log.debug("SOCKET initialize command sent. presetEqpId={}, remote={}",
                    presetEqpId,
                    channel.remoteAddress());
        }
        channel.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(cmd));
    }
}
