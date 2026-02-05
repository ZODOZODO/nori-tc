package com.nori.tc.db.core.model.upsert;

/**
 * tc_model_secs_message 갱신 입력(Command)
 *
 * - secs_msg_key로 대상 식별(대리키 기반)
 * - 변경 가능 필드만 포함합니다.
 *
 * 주의:
 * - updated_at은 DB(또는 구현체)에서 현재 시각으로 갱신되도록 처리하는 것을 권장합니다.
 */
public record UpsertTcModelSecsMessage(
        long secsMsgKey,
        long modelKey,
        String secsMsgName,
        String description,
        String dataIndex
) {
}
