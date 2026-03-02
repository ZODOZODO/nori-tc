package com.nori.tc.ui.adapters.web.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * DELETE /api/eqp/{eqpId} (EQP_DELETE) 요청 본문 DTO입니다.
 *
 * <p>eqpId는 경로 변수로 전달됩니다. KafkaUiTaskData 계약이 interfaceType을
 * 필수로 요구하므로 요청 본문을 통해 함께 전달합니다.</p>
 *
 * @param interfaceType 통신 인터페이스 타입 (필수)
 * @param uiMessage     UI 메시지 (선택)
 */
public record EqpDeleteRequest(

        @NotBlank(message = "interfaceType은 필수입니다.")
        String interfaceType,

        String uiMessage
) {
}
