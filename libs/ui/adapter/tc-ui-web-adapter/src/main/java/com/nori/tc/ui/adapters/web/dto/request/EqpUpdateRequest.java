package com.nori.tc.ui.adapters.web.dto.request;

import com.nori.tc.messaging.kafka.contract.GatewayEquipmentProfileSnapshot;
import jakarta.validation.constraints.NotBlank;

/**
 * PUT /api/eqp/{eqpId} (EQP_UPDATE) 요청 본문 DTO입니다.
 *
 * <p>eqpId는 경로 변수로 전달되므로 요청 본문에 포함하지 않습니다.
 * equipmentProfile에 변경된 설비 구성 스냅샷을 포함하면 Gateway가
 * DB 재조회 없이 EQP bean을 갱신합니다.</p>
 *
 * @param interfaceType    통신 인터페이스 타입 (필수)
 * @param uiMessage        UI 메시지 (선택)
 * @param equipmentProfile 변경된 설비 프로파일 스냅샷 (선택)
 */
public record EqpUpdateRequest(

        @NotBlank(message = "interfaceType은 필수입니다.")
        String interfaceType,

        String uiMessage,

        GatewayEquipmentProfileSnapshot equipmentProfile
) {
}
