package com.nori.tc.comm.gateway.runtime.channel;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 설비 채널 레지스트리를 이용해 outbound 프레임을 실제 채널로 전달하는 송신 포트 구현체입니다.
 *
 * <p>이 구현체는 "전송 시도"만 담당하며, 재시도/격리/스케줄링 정책은 상위
 * {@link com.nori.tc.comm.gateway.application.processing.EquipmentProcessingCoordinator}가 담당합니다.</p>
 *
 * <p>동작 규칙:</p>
 * <p>- eqpId에 활성 채널이 없으면 예외 발생</p>
 * <p>- 실제 네트워크 송신은 {@link EquipmentChannel#send(OutboundRawFrame)}에 위임</p>
 */
public final class ChannelBasedOutboundSender implements OutboundSenderPort {

    private static final Logger log = LoggerFactory.getLogger(ChannelBasedOutboundSender.class);
    private final EquipmentChannelRegistry registry;

    /**
     * 송신 포트 구현체를 초기화합니다.
     *
     * @param registry 설비 ID -> 채널 매핑 레지스트리
     */
    public ChannelBasedOutboundSender(final EquipmentChannelRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry is null");
        log.info("ChannelBasedOutboundSender initialized.");
    }

    /**
     * outbound raw frame을 대상 설비 채널로 전송합니다.
     *
     * @param frame 전송할 raw frame
     * @throws Exception 채널 미등록/비활성 또는 하위 채널 전송 실패 시 예외
     */
    @Override
    public void send(final OutboundRawFrame frame) throws Exception {
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
        // 실제 네트워크 송신은 어댑터(Netty 등)가 구현한 채널에 위임합니다.
        channel.send(frame);
    }
}
