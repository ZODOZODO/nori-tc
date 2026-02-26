package com.nori.tc.comm.gateway.context.service;

import com.nori.tc.comm.gateway.context.model.EquipmentContext;
import com.nori.tc.comm.gateway.context.model.EquipmentContextProfile;
import com.nori.tc.comm.gateway.context.port.EquipmentRuntimeCatalog;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * {@link EquipmentRuntimeCatalog}의 기본 구현체입니다.
 *
 * <p>핵심 정책:</p>
 * <p>1) 런타임 hot path에서는 DB fallback을 사용하지 않고 {@link EquipmentContextRegistry}만 조회합니다.</p>
 * <p>2) 실패 사유를 상태값으로 구분하여 호출부에서 운영 로그/오류코드를 일관되게 매핑할 수 있도록 합니다.</p>
 * <p>3) info 로그는 남기지 않고 debug 로그만 남겨 hot path 로그 노이즈를 억제합니다.</p>
 */
@Service
public class EquipmentRuntimeCatalogService implements EquipmentRuntimeCatalog {

    private static final Logger log = LoggerFactory.getLogger(EquipmentRuntimeCatalogService.class);

    private final EquipmentContextRegistry contextRegistry;

    /**
     * 런타임 카탈로그 구현체 의존성을 초기화합니다.
     *
     * @param contextRegistry 설비 컨텍스트 저장소(bean SSOT)
     */
    public EquipmentRuntimeCatalogService(final EquipmentContextRegistry contextRegistry) {
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
    }

    /**
     * eqpId 기준으로 인메모리 컨텍스트를 조회하여 런타임 메타를 반환합니다.
     *
     * @param eqpId 설비 ID
     * @return 조회 결과(성공/실패 사유 포함)
     */
    @Override
    public LookupResult find(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            if (log.isDebugEnabled()) {
                log.debug("런타임 메타 조회 실패. reason=INVALID_EQP_ID");
            }
            return LookupResult.miss(LookupStatus.INVALID_EQP_ID);
        }

        final String normalizedEqpId = eqpId.trim();
        final EquipmentContext context = contextRegistry.find(normalizedEqpId).orElse(null);
        if (context == null) {
            if (log.isDebugEnabled()) {
                log.debug("런타임 메타 조회 실패. eqpId={}, reason=CONTEXT_NOT_FOUND", normalizedEqpId);
            }
            return LookupResult.miss(LookupStatus.CONTEXT_NOT_FOUND);
        }

        final EquipmentContextProfile profile = context.profile();
        if (profile == null) {
            if (log.isDebugEnabled()) {
                log.debug("런타임 메타 조회 실패. eqpId={}, reason=PROFILE_NOT_FOUND", normalizedEqpId);
            }
            return LookupResult.miss(LookupStatus.PROFILE_NOT_FOUND);
        }

        final GatewayEquipmentInfo equipmentInfo = profile.equipmentInfo();
        if (equipmentInfo == null) {
            if (log.isDebugEnabled()) {
                log.debug("런타임 메타 조회 실패. eqpId={}, reason=EQUIPMENT_INFO_NOT_FOUND", normalizedEqpId);
            }
            return LookupResult.miss(LookupStatus.EQUIPMENT_INFO_NOT_FOUND);
        }

        if (log.isDebugEnabled()) {
            log.debug("런타임 메타 조회 성공. eqpId={}, interfaceType={}, mode={}, routePartition={}, enabled={}",
                    normalizedEqpId,
                    equipmentInfo.commInterfaceType(),
                    equipmentInfo.connectionMode(),
                    equipmentInfo.routePartition(),
                    equipmentInfo.enabled());
        }
        return LookupResult.found(equipmentInfo);
    }
}
