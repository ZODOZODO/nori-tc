package com.nori.tc.ui.adapters.web.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/eqp/{eqpId}/checkout 요청 본문 DTO입니다.
 *
 * @param sourceVersion 복사 기준 파라미터 버전 (필수)
 */
public record EqpCheckoutRequest(

        @NotBlank(message = "sourceVersion은 필수입니다.")
        String sourceVersion
) {
}
