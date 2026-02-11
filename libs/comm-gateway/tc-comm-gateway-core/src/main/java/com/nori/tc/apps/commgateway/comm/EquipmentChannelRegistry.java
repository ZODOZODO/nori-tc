package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentId;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * eqpId -> EquipmentChannel 매핑
 *
 * - 채널 등록/해제는 Netty acceptor/connector 레이어에서 수행합니다.
 * - 검색은 낮은 지연을 위해 ConcurrentHashMap 기반으로 단순화합니다.
 */
public final class EquipmentChannelRegistry {

    private final Map<String, EquipmentChannel> channels = new ConcurrentHashMap<>();

    /**
     * Bind eqpId to channel if not already bound.
     *
     * @return true if bound, false if duplicate connection detected.
     */
    public boolean tryBind(final EquipmentId equipmentId, final EquipmentChannel channel) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(channel, "channel is null");
        final String eqpId = equipmentId.value();

        final EquipmentChannel existing = channels.putIfAbsent(eqpId, channel);
        if (existing == null) {
            return true;
        }

        // If the existing channel is inactive, allow replacement.
        if (!existing.isActive()) {
            channels.remove(eqpId, existing);
            return channels.putIfAbsent(eqpId, channel) == null;
        }

        return false;
    }

    /**
     * Timeout hook for a specific eqpId/channel pair.
     *
     * This is used by timeout/health-check paths to evict a stale mapping
     * without accidentally removing a newer channel that replaced it.
     *
     * @return true if the mapping was removed, false otherwise.
     */
    public boolean timeout(final EquipmentId equipmentId, final EquipmentChannel channel) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(channel, "channel is null");
        return channels.remove(equipmentId.value(), channel);
    }

    public void unregister(final EquipmentId equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        channels.remove(equipmentId.value());
    }

    public void unregister(final EquipmentId equipmentId, final EquipmentChannel channel) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(channel, "channel is null");
        channels.remove(equipmentId.value(), channel);
    }

    public EquipmentChannel get(final EquipmentId equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        return channels.get(equipmentId.value());
    }
}
