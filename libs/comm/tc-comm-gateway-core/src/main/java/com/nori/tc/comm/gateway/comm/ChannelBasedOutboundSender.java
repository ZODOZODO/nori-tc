package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * eqpId 기반 outbound sender 구현체
 *
 * - eqpId에 매핑된 채널이 없으면 즉시 예외를 던집니다.
 * - 예외는 상위(EqpSequentialProcessor)에서 DLQ/Quarantine로 처리됩니다.
 */
public final class ChannelBasedOutboundSender implements OutboundSenderPort {

    private static final Logger log = LoggerFactory.getLogger(ChannelBasedOutboundSender.class);
    private final EquipmentChannelRegistry registry;

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param registry 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public ChannelBasedOutboundSender(final EquipmentChannelRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry is null");
    }

    
    /**
     * 게이트웨이 코어 모듈 메시지 또는 이벤트를 발행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param frame 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    @Override
    public void send(final OutboundRawFrame frame) throws Exception {
        // 출력 단계: 결과를 외부 저장소/브로커로 반영합니다.
        Objects.requireNonNull(frame, "frame is null");

        final EquipmentId equipmentId = frame.equipmentId();
        final EquipmentChannel channel = registry.get(equipmentId);

        if (channel == null || !channel.isActive()) {
            log.warn("Outbound send failed (no active channel). eqpId={}", equipmentId.value());
            throw new IllegalStateException("No channel registered for eqpId=" + equipmentId.value());
        }

        if (log.isDebugEnabled()) {
            log.debug("Outbound send to channel. eqpId={}, bytes={}", equipmentId.value(), frame.bytes().length);
        }
        channel.send(frame);
    }
}
