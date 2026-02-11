package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(EquipmentChannelRegistry.class);
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
            log.info("Channel bound. eqpId={}", eqpId);
            return true;
        }

        // If the existing channel is inactive, allow replacement.
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
        final boolean removed = channels.remove(equipmentId.value(), channel);
        if (removed) {
            log.info("Channel timeout evicted. eqpId={}", equipmentId.value());
        }
        return removed;
    }

    public void unregister(final EquipmentId equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        channels.remove(equipmentId.value());
        log.info("Channel unregistered. eqpId={}", equipmentId.value());
    }

    public void unregister(final EquipmentId equipmentId, final EquipmentChannel channel) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(channel, "channel is null");
        channels.remove(equipmentId.value(), channel);
        log.info("Channel unregistered (match). eqpId={}", equipmentId.value());
    }

    public EquipmentChannel get(final EquipmentId equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        return channels.get(equipmentId.value());
    }
}
