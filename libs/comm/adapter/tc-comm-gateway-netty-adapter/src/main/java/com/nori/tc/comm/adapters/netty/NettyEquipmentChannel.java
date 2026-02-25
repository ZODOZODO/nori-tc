package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.gateway.runtime.channel.EquipmentChannel;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Netty 채널 기반 {@link EquipmentChannel} 구현체입니다.
 *
 * <p>게이트웨이 공통 계층의 아웃바운드 프레임을 Netty `Channel`로 전달하는
 * 최소 어댑터 역할을 수행합니다.</p>
 */
public final class NettyEquipmentChannel implements EquipmentChannel {

    private static final Logger log = LoggerFactory.getLogger(NettyEquipmentChannel.class);

    /**
     * 실제 I/O를 수행하는 Netty 채널입니다.
     */
    private final Channel channel;

    /**
     * Netty 장비 채널 어댑터를 생성합니다.
     *
     * @param channel 대상 Netty 채널
     */
    public NettyEquipmentChannel(final Channel channel) {
        this.channel = Objects.requireNonNull(channel, "channel is null");
    }

    /**
     * 원본 Netty 채널을 반환합니다.
     *
     * @return native Netty 채널
     */
    public Channel nativeChannel() {
        return channel;
    }

    /**
     * 아웃바운드 프레임을 동기 전송합니다.
     *
     * <p>`writeAndFlush` 결과를 `sync()`로 대기하여
     * 상위 호출자가 즉시 성공/실패를 판단할 수 있도록 구성했습니다.</p>
     *
     * @param frame 전송할 raw frame
     */
    @Override
    public void send(final OutboundRawFrame frame) throws Exception {
        Objects.requireNonNull(frame, "frame is null");

        final ChannelFuture future = channel.writeAndFlush(Unpooled.wrappedBuffer(frame.bytes()));
        future.sync();

        logSocketOutboundPayloadIfNeeded(frame, future.isSuccess());

        if (!future.isSuccess()) {
            final Throwable cause = future.cause();
            withEqpLogContext(frame, () -> log.warn(
                    "Netty outbound send failed. eqpId={}, remoteAddress={}, payloadBytes={}, description={}, payload={}",
                    frame.equipmentId().value(),
                    channel.remoteAddress(),
                    frame.bytes().length,
                    frame.description(),
                    frame.commInterfaceType() == CommInterfaceType.SOCKET
                            ? SocketWirePayloadLogFormatter.describe(frame.bytes())
                            : "<non-socket-payload-omitted>",
                    cause
            ));
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new IllegalStateException("Netty send failed", cause);
        }
    }

    /**
     * 채널 활성 상태를 반환합니다.
     *
     * @return 활성 상태면 true
     */
    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    /**
     * 채널을 종료합니다.
     */
    @Override
    public void close() {
        channel.close();
    }

    /**
     * SOCKET 프로토콜 outbound raw payload를 로그로 남깁니다.
     *
     * <p>사용자 요구사항은 "무슨 메시지를 보냈는지"를 즉시 확인할 수 있어야 하는 것이므로,
     * 실제 wire bytes 기준 payload preview를 기록합니다. 로그량을 고려해 SOCKET만 상세 payload를 남깁니다.</p>
     *
     * @param frame 송신 frame
     * @param success Netty writeAndFlush 성공 여부
     */
    private void logSocketOutboundPayloadIfNeeded(final OutboundRawFrame frame, final boolean success) {
        if (frame.commInterfaceType() != CommInterfaceType.SOCKET) {
            return;
        }

        final Runnable logTask = () -> {
            final String payloadDescription = SocketWirePayloadLogFormatter.describe(frame.bytes());
            if (success) {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "SOCKET_WIRE_TX. eqpId={}, socketType={}, remote={}, description={}, payload={}",
                            frame.equipmentId().value(),
                            frame.socketType(),
                            channel.remoteAddress(),
                            frame.description(),
                            payloadDescription
                    );
                }
                return;
            }

            log.warn(
                    "SOCKET_WIRE_TX_FAILED. eqpId={}, socketType={}, remote={}, description={}, payload={}",
                    frame.equipmentId().value(),
                    frame.socketType(),
                    channel.remoteAddress(),
                    frame.description(),
                    payloadDescription
            );
        };

        withEqpLogContext(frame, logTask);
    }

    /**
     * eqpId가 있는 outbound frame 로그를 설비 로그 파일(EQP_SIFT)로 라우팅할 수 있도록 MDC를 적용합니다.
     *
     * @param frame outbound frame
     * @param task 실행할 로그 작업
     */
    private void withEqpLogContext(final OutboundRawFrame frame, final Runnable task) {
        if (task == null) {
            return;
        }
        if (frame == null || frame.equipmentId() == null || frame.equipmentId().value() == null
                || frame.equipmentId().value().isBlank()) {
            task.run();
            return;
        }

        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(frame.equipmentId().value())) {
            task.run();
        }
    }
}
