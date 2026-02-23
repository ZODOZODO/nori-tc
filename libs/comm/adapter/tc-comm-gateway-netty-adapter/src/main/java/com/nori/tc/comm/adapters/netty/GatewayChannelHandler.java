package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.application.ingress.GatewayIngressService;
import com.nori.tc.comm.gateway.config.props.GatewayNettyProperties;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogSampler;
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
 * <p>ACTIVE/PASSIVE 연결 경로를 공통으로 처리하며, 바인딩 전/후 상태를 관리합니다.</p>
 * <p>1) UNBOUND: eqpId가 확정되지 않은 상태(초기 프레임 수집/파싱 단계)</p>
 * <p>2) BOUND: eqpId가 확정된 상태(메일박스 enqueue 가능)</p>
 *
 * <p>성능 관점에서 I/O 스레드에서는 최소 작업(바이트 복사 + 큐 적재)만 수행하고,
 * 바인딩 파싱/검증은 별도 executor에서 수행합니다.</p>
 */
public final class GatewayChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GatewayChannelHandler.class);

    /**
     * 채널이 담당하는 인터페이스 타입입니다.
     */
    private final CommInterfaceType interfaceType;

    /**
     * 아웃바운드 채널(게이트웨이가 먼저 접속)에서 미리 알고 있는 목표 eqpId입니다.
     *
     * <p>null이면 수신 채널이며, initialize 응답/초기 프레임 파싱으로 eqpId를 결정합니다.</p>
     */
    private final String presetEqpId;
    /**
     * 채널 생성 시 확정된 SOCKET socketType 입니다.
     *
     * <p>SOCKET 채널은 initialize 송신/응답 파싱 단계부터 이 값을 사용하여
     * 프레이밍/디코드/인코드 핸들러를 일관되게 선택합니다.</p>
     */
    private final String socketType;

    private final GatewayNettyProperties nettyProperties;
    private final GatewayIngressService processingService;
    private final EqpBindingService bindingService;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;
    private final HsmsEqpIdExtractor hsmsExtractor;
    private final SocketEqpIdExtractor socketExtractor;
    private final BindAttemptExecutor bindExecutor;

    /**
     * 동일 채널에서 바인딩 시도가 중복으로 예약되지 않도록 제어합니다.
     */
    private final AtomicBoolean bindScheduled = new AtomicBoolean(false);

    /**
     * UNBOUND 상태에서 수신한 raw bytes를 임시 보관하는 버퍼입니다.
     */
    private final UnboundInbox unboundInbox;

    /**
     * 채널 핸들러를 생성합니다.
     *
     * @param interfaceType 인터페이스 타입
     * @param presetEqpId 아웃바운드 경로의 목표 eqpId(수신 채널이면 null)
     * @param nettyProperties Netty 설정
     * @param processingService inbound enqueue 진입점
     * @param bindingService 바인딩 서비스
     * @param metrics 메트릭 수집기
     * @param logSampler 로그 샘플링 정책
     * @param hsmsExtractor HSMS eqpId 추출기
     * @param socketExtractor SOCKET eqpId 추출기
     * @param bindExecutor 바인딩 시도 실행기
     */
    public GatewayChannelHandler(
            final CommInterfaceType interfaceType,
            final String presetEqpId,
            final String socketType,
            final GatewayNettyProperties nettyProperties,
            final GatewayIngressService processingService,
            final EqpBindingService bindingService,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler,
            final HsmsEqpIdExtractor hsmsExtractor,
            final SocketEqpIdExtractor socketExtractor,
            final BindAttemptExecutor bindExecutor
    ) {
        this.interfaceType = Objects.requireNonNull(interfaceType, "interfaceType is null");
        this.presetEqpId = (presetEqpId == null || presetEqpId.isBlank()) ? null : presetEqpId;
        this.socketType = normalizeSocketType(interfaceType, socketType);
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
        if (log.isDebugEnabled() && interfaceType == CommInterfaceType.SOCKET) {
            log.debug("SOCKET 채널 핸들러 생성. presetEqpId={}, socketType={}", this.presetEqpId, this.socketType);
        }
    }

    /**
     * 채널 활성화 시 UNBOUND 상태를 기록하고 바인딩 준비 절차를 시작합니다.
     *
     * <p>정책은 다음과 같습니다.</p>
     * <p>1) HSMS 아웃바운드(preset eqpId 있음)는 즉시 바인딩 시도</p>
     * <p>2) 그 외 경로는 bind timeout을 예약하고, SOCKET이면 initialize를 송신</p>
     *
     * @param ctx Netty 채널 컨텍스트
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        final Channel channel = ctx.channel();
        NettyChannelAttributes.setBindState(channel, BindState.UNBOUND);
        metrics.incrementActiveConnections();
        metrics.incrementUnboundConnections();

        if (shouldBindImmediatelyOnChannelActive()) {
            bindImmediatelyWithPresetEqpId(channel);
            return;
        }

        scheduleBindTimeout(channel);
        sendInitializeIfNeeded(channel);
    }

    /**
     * 채널 비활성화 시 bind timeout을 해제하고 메트릭/바인딩 상태를 정리합니다.
     *
     * @param ctx Netty 채널 컨텍스트
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
     * <p>BOUND 상태면 즉시 설비 mailbox로 enqueue하고,
     * UNBOUND 상태면 unbound inbox에 저장 후 바인딩 시도를 예약합니다.</p>
     *
     * @param ctx Netty 채널 컨텍스트
     * @param msg 수신 메시지(ByteBuf 기대)
     */
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            ReferenceCountUtil.release(msg);
            return;
        }

        try {
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
                withEqpLogContext(resolveLogEqpId(channel),
                        () -> log.warn("Unbound inbox overflow. closing channel. remote={}", channel.remoteAddress()));
                channel.close();
                return;
            }

            scheduleBindAttempt(channel);
        } finally {
            ReferenceCountUtil.release(buf);
        }
    }

    /**
     * 채널 예외 발생 시 로그를 남기고 채널을 종료합니다.
     *
     * @param ctx Netty 채널 컨텍스트
     * @param cause 예외 원인
     */
    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        final Channel channel = ctx.channel();
        withEqpLogContext(resolveLogEqpId(channel), () -> log.warn("Netty channel error", cause));
        ctx.close();
    }

    /**
     * 채널 활성 직후 즉시 바인딩 가능 여부를 반환합니다.
     *
     * <p>현재 정책: preset eqpId가 있고 인터페이스가 SOCKET이 아닌 경우 즉시 바인딩합니다.</p>
     *
     * @return 즉시 바인딩 대상이면 true
     */
    private boolean shouldBindImmediatelyOnChannelActive() {
        return presetEqpId != null && interfaceType != CommInterfaceType.SOCKET;
    }

    /**
     * preset eqpId 기반 즉시 바인딩을 수행합니다.
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

        withEqpLogContext(presetEqpId,
                () -> log.info("ACTIVE_BIND_OK. eqpId={}, remote={}", presetEqpId, channel.remoteAddress()));

        // HSMS 경로에서는 no-op이며, SOCKET 경로와 호출 구조를 통일하기 위해 유지합니다.
        sendInitializeIfNeeded(channel);
    }

    /**
     * 바인딩 시도를 executor에 예약합니다.
     *
     * <p>동일 채널에서 동시 예약은 1건만 허용합니다.</p>
     *
     * @param channel 대상 채널
     */
    private void scheduleBindAttempt(final Channel channel) {
        if (!bindScheduled.compareAndSet(false, true)) {
            return;
        }

        final Runnable bindTask = () -> {
            try {
                attemptBind(channel);
            } finally {
                bindScheduled.set(false);
            }
        };

        final String eqpIdForLog = resolveLogEqpId(channel);
        withEqpLogContext(eqpIdForLog, () -> bindExecutor.submit(GatewayLogContext.wrap(bindTask)));
    }

    /**
     * UNBOUND 채널에 대한 바인딩 시도를 수행합니다.
     *
     * <p>SOCKET 아웃바운드의 경우 initialize 응답의 eqpId가 preset eqpId와 일치해야 합니다.</p>
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
            unboundInbox.drainToBuffer();
            extractedEqpIdOpt = switch (interfaceType) {
                case HSMS -> hsmsExtractor.tryExtractEqpId(unboundInbox.buffer());
                case SOCKET -> socketExtractor.tryExtractEqpId(unboundInbox.buffer(), socketType);
            };
        } catch (Exception ex) {
            withEqpLogContext(resolveLogEqpId(channel),
                    () -> log.warn("Bind parsing failed. closing channel. remote={}, interfaceType={}, socketType={}",
                            channel.remoteAddress(),
                            interfaceType,
                            socketType,
                            ex));
            channel.close();
            return;
        }

        if (extractedEqpIdOpt.isEmpty()) {
            return;
        }

        final String extractedEqpId = extractedEqpIdOpt.get();
        final boolean outboundChannel = presetEqpId != null;

        if (outboundChannel
                && interfaceType == CommInterfaceType.SOCKET
                && !presetEqpId.equals(extractedEqpId)) {
            withEqpLogContext(presetEqpId, () -> log.warn(
                    "SOCKET_INITIALIZE_EQPID_MISMATCH. expectedEqpId={}, replyEqpId={}, socketType={}, remote={}",
                    presetEqpId,
                    extractedEqpId,
                    socketType,
                    channel.remoteAddress()
            ));
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

        withEqpLogContext(bindEqpId, () -> {
            if (outboundChannel) {
                log.info("ACTIVE_BIND_OK. eqpId={}, remote={}", bindEqpId, channel.remoteAddress());
            } else {
                log.info("PASSIVE_BIND_OK. eqpId={}, remote={}", bindEqpId, channel.remoteAddress());
            }
        });

        // 바인딩 성공 후 UNBOUND 임시 버퍼를 비웁니다.
        unboundInbox.clear();
    }

    /**
     * 바인딩 거절 결과를 코드별로 로깅합니다.
     *
     * @param result 바인딩 결과
     * @param eqpId 설비 ID
     * @param channel 대상 채널
     * @param bindPath 바인딩 경로 구분 문자열
     */
    private void logBindRejected(
            final EqpBindingService.BindResult result,
            final String eqpId,
            final Channel channel,
            final String bindPath
    ) {
        withEqpLogContext(eqpId, () -> {
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
        });
    }

    /**
     * UNBOUND 타임아웃 감시 작업을 예약합니다.
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
                    withEqpLogContext(resolveLogEqpId(channel), () -> log.warn(
                            "BIND_TIMEOUT. closing channel. seconds={}, interfaceType={}, presetEqpId={}, remote={}",
                            nettyProperties.getBindTimeoutSeconds(),
                            interfaceType,
                            presetEqpId,
                            channel.remoteAddress()
                    ));
                }
                channel.close();
            }
        }, nettyProperties.getBindTimeoutSeconds(), TimeUnit.SECONDS);

        NettyChannelAttributes.setBindTimeoutTask(channel, task);
        if (log.isDebugEnabled()) {
            withEqpLogContext(resolveLogEqpId(channel), () -> log.debug(
                    "Bind timeout scheduled. seconds={}, interfaceType={}, presetEqpId={}, remote={}",
                    nettyProperties.getBindTimeoutSeconds(),
                    interfaceType,
                    presetEqpId,
                    channel.remoteAddress()
            ));
        }
    }

    /**
     * 예약된 bind timeout 작업을 취소합니다.
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
     * SOCKET 채널에서 initialize 명령을 송신합니다.
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

        final byte[] cmd = socketExtractor.initializeCommandBytes(socketType);
        if (log.isDebugEnabled()) {
            withEqpLogContext(resolveLogEqpId(channel), () -> log.debug(
                    "SOCKET initialize command sent. presetEqpId={}, socketType={}, remote={}",
                    presetEqpId,
                    socketType,
                    channel.remoteAddress()
            ));
        }
        channel.writeAndFlush(Unpooled.wrappedBuffer(cmd));
    }

    /**
     * 로깅에 사용할 eqpId를 채널 상태에서 추론합니다.
     *
     * <p>우선순위: 채널 attribute eqpId -> presetEqpId</p>
     *
     * @param channel 대상 채널
     * @return 로깅용 eqpId(없으면 null)
     */
    private String resolveLogEqpId(final Channel channel) {
        if (channel == null) {
            return presetEqpId;
        }
        final String boundEqpId = NettyChannelAttributes.getEqpId(channel);
        if (boundEqpId != null && !boundEqpId.isBlank()) {
            return boundEqpId;
        }
        return presetEqpId;
    }

    /**
     * eqpId MDC를 적용한 상태로 작업을 실행합니다.
     *
     * @param eqpId 설비 ID
     * @param task 실행 작업
     */
    /**
     * 채널 생성 시 전달된 socketType을 인터페이스 타입 기준으로 정규화/검증합니다.
     *
     * <p>HSMS 채널은 socketType이 의미 없으므로 null을 유지하고,
     * SOCKET 채널은 blank 입력을 허용하지 않아 초기 바인딩 단계의 핸들러 선택 오류를 사전에 차단합니다.</p>
     *
     * @param interfaceType 채널 인터페이스 타입
     * @param socketType 외부에서 주입한 socketType
     * @return 정규화된 socketType (HSMS면 null)
     */
    private String normalizeSocketType(final CommInterfaceType interfaceType, final String socketType) {
        if (interfaceType != CommInterfaceType.SOCKET) {
            return null;
        }
        if (socketType == null || socketType.isBlank()) {
            throw new IllegalArgumentException("socketType is required for SOCKET channel");
        }
        return socketType.trim();
    }

    /**
     * eqpId MDC 컨텍스트를 적용한 상태로 작업을 실행합니다.
     *
     * <p>eqpId가 비어 있으면 MDC를 건드리지 않고 그대로 실행합니다.</p>
     *
     * @param eqpId 로그 MDC에 넣을 설비 ID
     * @param task 실행 작업
     */
    private void withEqpLogContext(final String eqpId, final Runnable task) {
        if (task == null) {
            return;
        }
        if (eqpId == null || eqpId.isBlank()) {
            task.run();
            return;
        }
        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
            task.run();
        }
    }
}
