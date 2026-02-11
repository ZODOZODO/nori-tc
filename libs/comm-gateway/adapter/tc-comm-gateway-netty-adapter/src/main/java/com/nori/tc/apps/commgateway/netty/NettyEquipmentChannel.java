package com.nori.tc.apps.commgateway.netty;

import com.nori.tc.apps.commgateway.comm.EquipmentChannel;
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

    public NettyEquipmentChannel(final Channel channel) {
        this.channel = Objects.requireNonNull(channel, "channel is null");
    }

    public Channel nativeChannel() {
        return channel;
    }

    @Override
    public void send(final OutboundRawFrame frame) throws Exception {
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

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public void close() {
        channel.close();
    }
}
