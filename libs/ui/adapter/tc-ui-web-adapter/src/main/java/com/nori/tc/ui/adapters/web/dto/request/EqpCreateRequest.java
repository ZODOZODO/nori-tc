package com.nori.tc.ui.adapters.web.dto.request;

import com.nori.tc.messaging.kafka.contract.GatewayEquipmentProfileSnapshot;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/eqp (EQP_CREATE) 요청 본문 DTO입니다.
 *
 * <p>equipmentProfile은 Gateway가 DB 재조회 없이 EQP bean을 초기화할 수 있도록
 * 설비 구성 스냅샷을 함께 전달합니다. 생략 시 Gateway가 DB에서 직접 조회합니다.</p>
 *
 * @param eqpId            설비 ID (필수)
 * @param interfaceType    통신 인터페이스 타입 (필수, 예: HSMS, SOCKET)
 * @param uiMessage        UI 메시지 (선택)
 * @param equipmentProfile 설비 프로파일 스냅샷 (선택)
 */
public record EqpCreateRequest(

        @NotBlank(message = "eqpId는 필수입니다.")
        String eqpId,

        @NotBlank(message = "interfaceType은 필수입니다.")
        String interfaceType,

        String uiMessage,

        GatewayEquipmentProfileSnapshot equipmentProfile
) {
}
