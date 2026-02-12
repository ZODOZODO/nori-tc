package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.context.EquipmentContext;
import com.nori.tc.comm.gateway.context.EquipmentContextProfile;
import com.nori.tc.comm.gateway.context.EquipmentContextProfileProvider;
import com.nori.tc.comm.gateway.context.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.context.EquipmentDesiredState;
import com.nori.tc.comm.gateway.context.EquipmentRuntimeState;
import com.nori.tc.comm.gateway.context.EquipmentStatePersistencePort;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * UI 런타임 제어에서 "컨텍스트/프로파일" 책임을 전담하는 서비스입니다.
 *
 * <p>역할:
 * 1) eqpId/interfaceType 입력 검증
 * 2) DB 프로파일 조회 + EquipmentContext 로딩/갱신
 * 3) CREATE/UPDATE 처리 시 상태 이력 반영
 * 4) 공통 장비 검증 유틸 제공</p>
 */
@Service
public class GatewayUiContextCommandService {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiContextCommandService.class);

    private final EquipmentContextRegistry contextRegistry;
    private final EquipmentContextProfileProvider profileProvider;
    private final EquipmentStatePersistencePort statePersistencePort;

    /**
     * 컨텍스트/프로파일 관련 의존성을 초기화합니다.
     */
    public GatewayUiContextCommandService(
            final EquipmentContextRegistry contextRegistry,
            final EquipmentContextProfileProvider profileProvider,
            final ObjectProvider<EquipmentStatePersistencePort> statePersistencePortProvider
    ) {
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
        this.profileProvider = Objects.requireNonNull(profileProvider, "profileProvider is null");
        this.statePersistencePort = statePersistencePortProvider.getIfAvailable(() -> EquipmentStatePersistencePort.NO_OP);
    }

    /**
     * CREATE/UPDATE 요청을 처리하여 컨텍스트를 갱신합니다.
     *
     * <p>동작 규칙:
     * 1) eqpId/interfaceType 검증
     * 2) 프로파일 조회
     * 3) 기존 컨텍스트의 desired/runtime 상태를 보존하며 profile만 최신화
     * 4) create/update 이력 반영</p>
     */
    public GatewayEquipmentInfo createOrUpdateContext(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String eventType
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);

        final EquipmentContextProfile profile = profileProvider.findProfileById(normalizedEqpId).orElseThrow(
                () -> new GatewayUiTaskProcessingException(
                        GatewayUiTaskErrorCode.EQP_NOT_FOUND,
                        "Equipment profile not found"
                )
        );
        final GatewayEquipmentInfo equipmentInfo = profile.equipmentInfo();
        validateInterfaceType(equipmentInfo, requestedType);

        final EquipmentContext existing = contextRegistry.find(normalizedEqpId).orElse(null);
        final EquipmentDesiredState desiredState = existing == null
                ? (equipmentInfo.enabled() ? EquipmentDesiredState.STARTED : EquipmentDesiredState.ENDED)
                : existing.desiredState();
        final EquipmentRuntimeState runtimeState = existing == null
                ? (equipmentInfo.enabled() ? EquipmentRuntimeState.DISCONNECTED : EquipmentRuntimeState.REGISTERED)
                : existing.runtimeState();

        contextRegistry.upsertProfile(profile, desiredState, runtimeState, eventType, traceId);
        statePersistencePort.recordCreateOrUpdate(
                normalizedEqpId,
                traceId,
                eventType,
                "UI create/update request processed"
        );

        log.info("UI context upsert completed. eventType={}, eqpId={}, traceId={}, enabled={}",
                eventType, normalizedEqpId, traceId, equipmentInfo.enabled());
        return equipmentInfo;
    }

    /**
     * START/END/SEND_MESSAGE 경로에서 공통으로 사용하는 "컨텍스트 조회 + 없으면 로드" 동작입니다.
     */
    public EquipmentContext resolveOrLoadContext(
            final String eqpId,
            final String traceId,
            final String eventType
    ) {
        final String normalizedEqpId = requireEqpId(eqpId);
        return contextRegistry.find(normalizedEqpId).orElseGet(() -> {
            final EquipmentContextProfile profile = profileProvider.findProfileById(normalizedEqpId).orElseThrow(
                    () -> new GatewayUiTaskProcessingException(
                            GatewayUiTaskErrorCode.EQP_NOT_FOUND,
                            "Equipment profile not found"
                    )
            );
            final GatewayEquipmentInfo info = profile.equipmentInfo();

            final EquipmentDesiredState desiredState = info.enabled()
                    ? EquipmentDesiredState.STARTED
                    : EquipmentDesiredState.ENDED;
            final EquipmentRuntimeState runtimeState = info.enabled()
                    ? EquipmentRuntimeState.DISCONNECTED
                    : EquipmentRuntimeState.REGISTERED;

            final EquipmentContext created = contextRegistry.upsertProfile(
                    profile,
                    desiredState,
                    runtimeState,
                    eventType,
                    traceId
            );
            if (log.isDebugEnabled()) {
                log.debug("UI context loaded on demand. eqpId={}, eventType={}, traceId={}",
                        normalizedEqpId, eventType, traceId);
            }
            return created;
        });
    }

    /**
     * 장비와 요청 interfaceType 정합성을 검증하고 장비 정보를 반환합니다.
     */
    public GatewayEquipmentInfo resolveAndValidateEquipment(
            final String eqpId,
            final String interfaceType,
            final String traceId,
            final String eventType
    ) {
        final CommInterfaceType requestedType = parseInterfaceType(interfaceType);
        final EquipmentContext context = resolveOrLoadContext(eqpId, traceId, eventType);
        final GatewayEquipmentInfo equipmentInfo = context.profile().equipmentInfo();
        validateInterfaceType(equipmentInfo, requestedType);
        return equipmentInfo;
    }

    /**
     * stopRuntime 같은 legacy 경로에서 interfaceType 문자열을 얻을 때 사용합니다.
     */
    public String resolveInterfaceTypeName(final String eqpId) {
        final EquipmentContext context = resolveOrLoadContext(eqpId, "LEGACY_INTERFACE_RESOLVE", "LEGACY_INTERFACE_RESOLVE");
        return context.profile().equipmentInfo().commInterfaceType().name();
    }

    /**
     * create/update 외 상태 이벤트(START/END/DELETE)의 이력 반영을 노출합니다.
     */
    public void recordStartState(final String eqpId, final String traceId, final String detailMessage) {
        statePersistencePort.recordStart(eqpId, traceId, detailMessage);
    }

    /**
     * END 상태 이력 반영입니다.
     */
    public void recordEndState(final String eqpId, final String traceId, final String detailMessage) {
        statePersistencePort.recordEnd(eqpId, traceId, detailMessage);
    }

    /**
     * DELETE 상태 이력 반영입니다.
     */
    public void recordDeleteState(final String eqpId, final String traceId, final String detailMessage) {
        statePersistencePort.recordDelete(eqpId, traceId, detailMessage);
    }

    /**
     * 요청 interfaceType 문자열을 enum으로 변환합니다.
     */
    public CommInterfaceType parseInterfaceType(final String interfaceType) {
        try {
            return CommInterfaceType.fromText(interfaceType);
        } catch (Exception ex) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.INVALID_INTERFACE_TYPE,
                    "interfaceType is invalid"
            );
        }
    }

    /**
     * eqpId 필수값 검증 및 trim 결과를 반환합니다.
     */
    public String requireEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.EQP_ID_REQUIRED,
                    "eqpId is required"
            );
        }
        return eqpId.trim();
    }

    /**
     * 요청 interfaceType과 장비 프로파일 interfaceType 일치 여부를 검증합니다.
     */
    public void validateInterfaceType(
            final GatewayEquipmentInfo equipmentInfo,
            final CommInterfaceType requestedType
    ) {
        if (equipmentInfo.commInterfaceType() != requestedType) {
            throw new GatewayUiTaskProcessingException(
                    GatewayUiTaskErrorCode.INTERFACE_MISMATCH,
                    "Requested interfaceType does not match equipment profile"
            );
        }
    }
}
