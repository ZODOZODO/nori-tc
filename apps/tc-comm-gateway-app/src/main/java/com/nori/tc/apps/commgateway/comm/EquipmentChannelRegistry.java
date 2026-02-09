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

    public void register(final EquipmentId equipmentId, final EquipmentChannel channel) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(channel, "channel is null");
        channels.put(equipmentId.value(), channel);
    }

    public void unregister(final EquipmentId equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        channels.remove(equipmentId.value());
    }

    public EquipmentChannel get(final EquipmentId equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        return channels.get(equipmentId.value());
    }
}
