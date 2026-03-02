package com.nori.tc.ui.adapters.web.dto.response;

/**
 * GET /api/async/{traceId} polling 응답 DTO입니다.
 *
 * <p>eqp_start / eqp_end 요청 후 Gateway reply가 Redis에 저장된 경우 200으로 반환됩니다.
 * 아직 처리 중이거나 TTL이 만료된 경우 404가 반환됩니다.</p>
 *
 * @param traceId       작업 추적 ID
 * @param eqpId         대상 설비 ID
 * @param status        처리 상태 ({@code PASS} 또는 {@code FAIL})
 * @param errorCode     오류 코드 (status=FAIL 시 원인 코드, 성공 시 null)
 * @param errorMsg      오류 메시지 (status=FAIL 시 상세 설명, 성공 시 null)
 */
public record AsyncResultResponse(
        String traceId,
        String eqpId,
        String status,
        String errorCode,
        String errorMsg
) {
}
