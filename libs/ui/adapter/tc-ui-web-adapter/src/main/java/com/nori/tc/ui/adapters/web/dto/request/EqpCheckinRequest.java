package com.nori.tc.ui.adapters.web.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/eqp/{eqpId}/checkin 요청 본문 DTO입니다.
 *
 * @param newVersion 저장할 신규 버전 이름 (필수, 예: "v1.1")
 * @param description 버전 설명 (선택)
 */
public record EqpCheckinRequest(

        @NotBlank(message = "newVersion은 필수입니다.")
        String newVersion,

        String description
) {
}
