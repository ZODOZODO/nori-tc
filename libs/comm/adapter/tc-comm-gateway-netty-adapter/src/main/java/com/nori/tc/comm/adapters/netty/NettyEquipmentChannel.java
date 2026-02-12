package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.comm.EquipmentChannel;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.util.Objects;

/**
 * Netty 기반 EquipmentChannel 구현체.
 */
public final class NettyEquipmentChannel implements EquipmentChannel {

    private final Channel channel;

    
    /**
     * 게이트웨이 Netty 어댑터 구성 요소를 초기화합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channel 통신 채널/세션 정보
     */
    public NettyEquipmentChannel(final Channel channel) {
        this.channel = Objects.requireNonNull(channel, "channel is null");
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public Channel nativeChannel() {
        return channel;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 메시지 또는 이벤트를 발행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param frame 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    @Override
    public void send(final OutboundRawFrame frame) throws Exception {
        // 출력 단계: 결과를 외부 저장소/브로커로 반영합니다.
        Objects.requireNonNull(frame, "frame is null");

        final ChannelFuture future = channel.writeAndFlush(Unpooled.wrappedBuffer(frame.bytes()));
        future.sync();

        if (!future.isSuccess()) {
            final Throwable cause = future.cause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new IllegalStateException("Netty send failed", cause);
        }
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 처리 성공 여부
     */
    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    
    /**
     * 게이트웨이 Netty 어댑터 리소스를 정리하고 종료합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     */
    @Override
    public void close() {
        channel.close();
    }
}
