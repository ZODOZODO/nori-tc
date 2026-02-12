package com.nori.tc.comm.adapters.netty;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.concurrent.ScheduledFuture;

/**
 * 게이트웨이 바인딩 상태 관리를 위한 Netty Channel Attribute 키 모음.
 *
 * 사용 항목
 * - EQP_ID: 채널에 바인딩된 장비 식별자
 * - BIND_STATE: 채널 바인딩 상태(UNBOUND/BOUND)
 * - BIND_TIMEOUT_TASK: UNBOUND 상태 타임아웃 감시 스케줄 작업 핸들
 */
public final class NettyChannelAttributes {

    public static final AttributeKey<String> EQP_ID = AttributeKey.valueOf("eqpId");
    public static final AttributeKey<BindState> BIND_STATE = AttributeKey.valueOf("bindState");
    public static final AttributeKey<ScheduledFuture<?>> BIND_TIMEOUT_TASK = AttributeKey.valueOf("bindTimeoutTask");

    
    /**
     * 게이트웨이 Netty 어댑터 구성 요소를 초기화합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     */
    private NettyChannelAttributes() {
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channel 통신 채널/세션 정보
     * @param eqpId 설비 식별 정보
     */
    public static void setEqpId(final Channel channel, final String eqpId) {
        channel.attr(EQP_ID).set(eqpId);
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channel 통신 채널/세션 정보
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public static String getEqpId(final Channel channel) {
        return channel.attr(EQP_ID).get();
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channel 통신 채널/세션 정보
     * @param state 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public static void setBindState(final Channel channel, final BindState state) {
        channel.attr(BIND_STATE).set(state);
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channel 통신 채널/세션 정보
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public static BindState getBindState(final Channel channel) {
        return channel.attr(BIND_STATE).get();
    }

    
    /**
     * 게이트웨이 Netty 어댑터 설정 값을 반영합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channel 통신 채널/세션 정보
     * @param task 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public static void setBindTimeoutTask(final Channel channel, final ScheduledFuture<?> task) {
        channel.attr(BIND_TIMEOUT_TASK).set(task);
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channel 통신 채널/세션 정보
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public static ScheduledFuture<?> getBindTimeoutTask(final Channel channel) {
        return channel.attr(BIND_TIMEOUT_TASK).get();
    }
}
