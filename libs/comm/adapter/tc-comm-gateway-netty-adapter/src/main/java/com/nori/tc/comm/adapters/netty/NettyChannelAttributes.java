package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.runtime.channel.EquipmentChannel;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

/**
 * Netty 채널에 바인딩 관련 상태를 저장하기 위한 Attribute 유틸리티입니다.
 *
 * <p>저장 항목:</p>
 * <p>1) EQP_ID: 채널에 바인딩된 설비 식별자</p>
 * <p>2) BIND_STATE: 채널 바인딩 상태(UNBOUND/BOUND)</p>
 * <p>3) BOUND_EQUIPMENT_CHANNEL: 레지스트리/메일박스에 바인딩된 EquipmentChannel 인스턴스</p>
 * <p>4) BIND_TIMEOUT_TASK: UNBOUND 타임아웃 감시 스케줄 작업</p>
 */
public final class NettyChannelAttributes {

    public static final AttributeKey<String> EQP_ID = AttributeKey.valueOf("eqpId");
    public static final AttributeKey<BindState> BIND_STATE = AttributeKey.valueOf("bindState");
    public static final AttributeKey<EquipmentChannel> BOUND_EQUIPMENT_CHANNEL =
            AttributeKey.valueOf("boundEquipmentChannel");
    public static final AttributeKey<ScheduledFuture<?>> BIND_TIMEOUT_TASK = AttributeKey.valueOf("bindTimeoutTask");

    /**
     * 유틸리티 클래스이므로 외부 인스턴스 생성을 막습니다.
     */
    private NettyChannelAttributes() {
    }

    /**
     * 채널에 eqpId를 기록합니다.
     *
     * @param channel 대상 채널
     * @param eqpId 설비 식별자
     */
    public static void setEqpId(final Channel channel, final String eqpId) {
        Objects.requireNonNull(channel, "channel is null").attr(EQP_ID).set(eqpId);
    }

    /**
     * 채널에 기록된 eqpId를 조회합니다.
     *
     * @param channel 대상 채널
     * @return eqpId(없으면 null)
     */
    public static String getEqpId(final Channel channel) {
        return Objects.requireNonNull(channel, "channel is null").attr(EQP_ID).get();
    }

    /**
     * 채널의 바인딩 상태를 기록합니다.
     *
     * @param channel 대상 채널
     * @param state 바인딩 상태
     */
    public static void setBindState(final Channel channel, final BindState state) {
        Objects.requireNonNull(channel, "channel is null").attr(BIND_STATE).set(state);
    }

    /**
     * 채널의 바인딩 상태를 조회합니다.
     *
     * @param channel 대상 채널
     * @return 바인딩 상태(없으면 null)
     */
    public static BindState getBindState(final Channel channel) {
        return Objects.requireNonNull(channel, "channel is null").attr(BIND_STATE).get();
    }

    /**
     * 채널에 바인딩 성공 시 사용된 EquipmentChannel 어댑터 인스턴스를 기록합니다.
     *
     * <p>빠른 재연결/채널 교체 레이스에서 "이전 채널의 channelInactive"가
     * 새 채널의 레지스트리/메일박스 매핑을 잘못 제거하지 않도록, unbind 단계에서
     * eqpId + channel 인스턴스 일치 조건으로 정리할 때 사용합니다.</p>
     *
     * @param channel 대상 Netty 채널
     * @param equipmentChannel 바인딩된 EquipmentChannel 인스턴스(해제 시 null 가능)
     */
    public static void setBoundEquipmentChannel(final Channel channel, final EquipmentChannel equipmentChannel) {
        Objects.requireNonNull(channel, "channel is null").attr(BOUND_EQUIPMENT_CHANNEL).set(equipmentChannel);
    }

    /**
     * 채널에 기록된 EquipmentChannel 어댑터 인스턴스를 조회합니다.
     *
     * @param channel 대상 Netty 채널
     * @return 바인딩 시 사용된 EquipmentChannel 인스턴스(없으면 null)
     */
    public static EquipmentChannel getBoundEquipmentChannel(final Channel channel) {
        return Objects.requireNonNull(channel, "channel is null").attr(BOUND_EQUIPMENT_CHANNEL).get();
    }

    /**
     * 채널의 bind timeout 감시 작업을 기록합니다.
     *
     * @param channel 대상 채널
     * @param task 스케줄 작업(없으면 null)
     */
    public static void setBindTimeoutTask(final Channel channel, final ScheduledFuture<?> task) {
        Objects.requireNonNull(channel, "channel is null").attr(BIND_TIMEOUT_TASK).set(task);
    }

    /**
     * 채널의 bind timeout 감시 작업을 조회합니다.
     *
     * @param channel 대상 채널
     * @return 스케줄 작업(없으면 null)
     */
    public static ScheduledFuture<?> getBindTimeoutTask(final Channel channel) {
        return Objects.requireNonNull(channel, "channel is null").attr(BIND_TIMEOUT_TASK).get();
    }
}
