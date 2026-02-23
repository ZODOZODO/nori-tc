package com.nori.tc.comm.gateway.runtime.channel;

import com.nori.tc.comm.core.eqp.EquipmentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 설비 ID(`eqpId`) 기준 활성 통신 채널 레지스트리입니다.
 *
 * <p>채널 등록/해제는 Netty acceptor/connector 어댑터 레이어에서 수행하고,
 * 코어는 이 레지스트리를 통해 채널 조회/교체/해제를 수행합니다.</p>
 *
 * <p>구현 메모:</p>
 * <p>- 조회 지연 최소화를 위해 {@link ConcurrentHashMap} 기반으로 단순 구성</p>
 * <p>- 중복 연결 시 기존 채널 활성 상태를 확인하여 비활성 채널만 교체 허용</p>
 */
public final class EquipmentChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(EquipmentChannelRegistry.class);
    private final Map<String, EquipmentChannel> channels = new ConcurrentHashMap<>();

    /**
     * 설비 ID에 채널을 바인딩합니다.
     *
     * <p>이미 동일 eqpId가 바인딩되어 있더라도 기존 채널이 비활성 상태이면 교체를 허용합니다.</p>
     *
     * @param equipmentId 설비 ID
     * @param channel 새로 바인딩할 채널
     * @return 바인딩/재바인딩 성공 시 {@code true}, 활성 중복 연결이면 {@code false}
     */
    public boolean tryBind(final EquipmentId equipmentId, final EquipmentChannel channel) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(channel, "channel is null");
        final String eqpId = equipmentId.value();

        final EquipmentChannel existing = channels.putIfAbsent(eqpId, channel);
        if (existing == null) {
            log.info("Channel bound. eqpId={}", eqpId);
            return true;
        }

        // 기존 채널이 이미 죽은 상태라면 stale mapping으로 간주하고 교체를 허용합니다.
        if (!existing.isActive()) {
            channels.remove(eqpId, existing);
            final boolean rebound = channels.putIfAbsent(eqpId, channel) == null;
            if (rebound) {
                log.info("Channel rebound (previous inactive). eqpId={}", eqpId);
            }
            return rebound;
        }

        if (log.isDebugEnabled()) {
            log.debug("Duplicate channel bind rejected. eqpId={}", eqpId);
        }
        return false;
    }

    /**
     * timeout/health-check 경로에서 특정 (eqpId, channel) 쌍만 안전하게 제거합니다.
     *
     * <p>새 채널이 이미 재바인딩된 경우 잘못 제거하지 않도록 "키+값 일치" 조건으로 삭제합니다.</p>
     *
     * @param equipmentId 설비 ID
     * @param channel 제거 대상 채널(기대한 기존 채널)
     * @return 실제로 해당 매핑이 제거되었으면 {@code true}
     */
    public boolean timeout(final EquipmentId equipmentId, final EquipmentChannel channel) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(channel, "channel is null");
        final boolean removed = channels.remove(equipmentId.value(), channel);
        if (removed) {
            log.info("Channel timeout evicted. eqpId={}", equipmentId.value());
        }
        return removed;
    }

    /**
     * 설비 ID 기준으로 채널 매핑을 제거합니다.
     *
     * @param equipmentId 설비 ID
     */
    public void unregister(final EquipmentId equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        channels.remove(equipmentId.value());
        log.info("Channel unregistered. eqpId={}", equipmentId.value());
    }

    /**
     * 설비 ID와 채널이 모두 일치하는 경우에만 채널 매핑을 제거합니다.
     *
     * @param equipmentId 설비 ID
     * @param channel 제거 대상 채널
     */
    public void unregister(final EquipmentId equipmentId, final EquipmentChannel channel) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(channel, "channel is null");
        channels.remove(equipmentId.value(), channel);
        log.info("Channel unregistered (match). eqpId={}", equipmentId.value());
    }

    /**
     * 설비 ID에 바인딩된 현재 채널을 조회합니다.
     *
     * @param equipmentId 설비 ID
     * @return 바인딩된 채널, 없으면 {@code null}
     */
    public EquipmentChannel get(final EquipmentId equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        final EquipmentChannel channel = channels.get(equipmentId.value());
        if (channel == null && log.isDebugEnabled()) {
            log.debug("Channel lookup missed. eqpId={}", equipmentId.value());
        }
        return channel;
    }
}
