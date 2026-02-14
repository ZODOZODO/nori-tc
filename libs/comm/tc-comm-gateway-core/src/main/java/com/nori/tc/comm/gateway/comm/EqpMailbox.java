package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;

import java.util.Objects;

/**
 * 설비(eqpId) 단위 런타임 mailbox입니다.
 *
 * <p>핵심 책임:</p>
 * <p>1) 설비별 inbound/outbound bounded 큐 보관</p>
 * <p>2) 현재 활성 채널 참조 유지</p>
 * <p>3) 순차 처리에 필요한 컨텍스트 접근점 제공</p>
 *
 * <p>주의:</p>
 * <p>- 스케줄링 상태(in-flight/scheduled)는 공통 {@code tc-common-mailbox}에서 관리합니다.</p>
 * <p>- 본 객체는 데이터 보관과 채널 참조에만 집중합니다.</p>
 */
public final class EqpMailbox {

    private final String eqpId;
    private final CommInterfaceType commInterfaceType;
    private final EquipmentRuntimeContext context;
    private final BoundedInboundQueue inboundQueue;
    private final BoundedOutboundQueue outboundQueue;

    private volatile EquipmentChannel channel;

    /**
     * 설비 mailbox를 생성합니다.
     *
     * @param eqpId 설비 ID
     * @param commInterfaceType 통신 인터페이스 타입
     * @param context 설비 런타임 컨텍스트
     * @param inboundQueue inbound bounded 큐
     * @param outboundQueue outbound bounded 큐
     */
    public EqpMailbox(
            final String eqpId,
            final CommInterfaceType commInterfaceType,
            final EquipmentRuntimeContext context,
            final BoundedInboundQueue inboundQueue,
            final BoundedOutboundQueue outboundQueue
    ) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        this.eqpId = eqpId.trim();
        this.commInterfaceType = Objects.requireNonNull(commInterfaceType, "commInterfaceType is null");
        this.context = Objects.requireNonNull(context, "context is null");
        this.inboundQueue = Objects.requireNonNull(inboundQueue, "inboundQueue is null");
        this.outboundQueue = Objects.requireNonNull(outboundQueue, "outboundQueue is null");
    }

    /**
     * 설비 ID를 반환합니다.
     *
     * @return 설비 ID
     */
    public String eqpId() {
        return eqpId;
    }

    /**
     * 통신 인터페이스 타입을 반환합니다.
     *
     * @return 통신 인터페이스 타입
     */
    public CommInterfaceType commInterfaceType() {
        return commInterfaceType;
    }

    /**
     * 설비 런타임 컨텍스트를 반환합니다.
     *
     * @return 설비 런타임 컨텍스트
     */
    public EquipmentRuntimeContext context() {
        return context;
    }

    /**
     * inbound 큐를 반환합니다.
     *
     * @return inbound bounded 큐
     */
    public BoundedInboundQueue inboundQueue() {
        return inboundQueue;
    }

    /**
     * outbound 큐를 반환합니다.
     *
     * @return outbound bounded 큐
     */
    public BoundedOutboundQueue outboundQueue() {
        return outboundQueue;
    }

    /**
     * 현재 활성 채널을 반환합니다.
     *
     * @return 활성 채널(없으면 null)
     */
    public EquipmentChannel channel() {
        return channel;
    }

    /**
     * mailbox에 활성 채널을 바인딩합니다.
     *
     * @param channel 활성 채널
     */
    public void bindChannel(final EquipmentChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel is null");
    }

    /**
     * 채널 참조를 제거합니다.
     */
    public void clearChannel() {
        this.channel = null;
    }
}
