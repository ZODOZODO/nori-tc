package com.nori.tc.apps.commgateway.netty;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.concurrent.ScheduledFuture;

/**
 * Netty channel attribute keys for gateway binding.
 *
 * - EQP_ID           : 바인딩된 eqpId
 * - BIND_STATE       : UNBOUND / BOUND 상태
 * - BIND_TIMEOUT_TASK: UNBOUND 타임아웃 스케줄
 */
public final class NettyChannelAttributes {

    public static final AttributeKey<String> EQP_ID = AttributeKey.valueOf("eqpId");
    public static final AttributeKey<BindState> BIND_STATE = AttributeKey.valueOf("bindState");
    public static final AttributeKey<ScheduledFuture<?>> BIND_TIMEOUT_TASK = AttributeKey.valueOf("bindTimeoutTask");

    private NettyChannelAttributes() {
    }

    public static void setEqpId(final Channel channel, final String eqpId) {
        channel.attr(EQP_ID).set(eqpId);
    }

    public static String getEqpId(final Channel channel) {
        return channel.attr(EQP_ID).get();
    }

    public static void setBindState(final Channel channel, final BindState state) {
        channel.attr(BIND_STATE).set(state);
    }

    public static BindState getBindState(final Channel channel) {
        return channel.attr(BIND_STATE).get();
    }

    public static void setBindTimeoutTask(final Channel channel, final ScheduledFuture<?> task) {
        channel.attr(BIND_TIMEOUT_TASK).set(task);
    }

    public static ScheduledFuture<?> getBindTimeoutTask(final Channel channel) {
        return channel.attr(BIND_TIMEOUT_TASK).get();
    }
}
