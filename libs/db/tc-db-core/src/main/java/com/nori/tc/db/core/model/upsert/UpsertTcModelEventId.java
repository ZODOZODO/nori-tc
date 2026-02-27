package com.nori.tc.db.core.model.upsert;

/**
 * tc_model_eventid upsert 입력(Command)
 *
 * 상세 규칙:
 * - modelVersionKey/eventId는 유니크 키이며, 이를 기준으로 upsert합니다.
 * - reportId는 nullable입니다.
 * - enabled가 null이면 DB 기본값(false)을 사용합니다.
 */
public record UpsertTcModelEventId(
        Long modelVersionKey,
        String eventId,
        String reportId,
        Boolean enabled
) {
}
