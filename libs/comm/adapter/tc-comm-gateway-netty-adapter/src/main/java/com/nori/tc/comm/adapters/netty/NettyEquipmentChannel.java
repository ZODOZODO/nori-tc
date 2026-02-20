package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.gateway.comm.EquipmentChannel;
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

        if (!future.isSuccess()) {
            final Throwable cause = future.cause();
            log.warn("Netty outbound send failed. remoteAddress={}, payloadBytes={}",
                    channel.remoteAddress(),
                    frame.bytes().length,
                    cause);
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
}
