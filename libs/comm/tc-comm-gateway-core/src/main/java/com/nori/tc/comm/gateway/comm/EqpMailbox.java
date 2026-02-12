package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * eqpId 단위 런타임 mailbox.
 *
 * - inbound/outbound 큐
 * - 스케줄링 플래그(scheduled)
 * - in-flight 제어 플래그(inFlight)
 */
/**
 * Per-equipment mailbox.
 *
 * Responsibilities:
 * - Hold bounded inbound/outbound queues for a single eqpId.
 * - Track scheduling/in-flight flags to enforce sequential processing.
 * - Keep a reference to the active channel for outbound writes.
 *
 * Notes:
 * - inboundQueue is shared with the runtime context so parsing sees the same data.
 * - scheduled/inFlight are used by EqpProcessingCoordinator to avoid duplicate work.
 */
public final class EqpMailbox {

    private final String eqpId;
    private final CommInterfaceType commInterfaceType;
    private final EquipmentRuntimeContext context;
    private final BoundedInboundQueue inboundQueue;
    private final BoundedOutboundQueue outboundQueue;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    private volatile EquipmentChannel channel;

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @param commInterfaceType 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param context 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param inboundQueue 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param outboundQueue 게이트웨이 코어 모듈 처리에 사용하는 입력 값
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
        this.eqpId = eqpId;
        this.commInterfaceType = Objects.requireNonNull(commInterfaceType, "commInterfaceType is null");
        this.context = Objects.requireNonNull(context, "context is null");
        this.inboundQueue = Objects.requireNonNull(inboundQueue, "inboundQueue is null");
        this.outboundQueue = Objects.requireNonNull(outboundQueue, "outboundQueue is null");
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public String eqpId() {
        return eqpId;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public CommInterfaceType commInterfaceType() {
        return commInterfaceType;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public EquipmentRuntimeContext context() {
        return context;
    }

    /**
     * Bounded inbound queue for this equipment.
     *
     * This queue is shared with the runtime context so that the sequential
     * processor and enqueue path see the same data structure.
     */
    public BoundedInboundQueue inboundQueue() {
        return inboundQueue;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public BoundedOutboundQueue outboundQueue() {
        return outboundQueue;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public EquipmentChannel channel() {
        return channel;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param channel 통신 채널/세션 정보
     */
    public void bindChannel(final EquipmentChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel is null");
    }

    
    /**
     * 게이트웨이 코어 모듈 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void clearChannel() {
        this.channel = null;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public AtomicBoolean scheduledFlag() {
        return scheduled;
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public AtomicBoolean inFlightFlag() {
        return inFlight;
    }
}
