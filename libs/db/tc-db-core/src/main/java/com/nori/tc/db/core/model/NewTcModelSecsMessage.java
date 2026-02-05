package com.nori.tc.db.core.model;

/**
 * tc_model_secs_message 생성 입력(Command)
 *
 * - secs_msg_key, updated_at은 DB에서 생성/갱신되므로 입력에서 제외합니다.
 * - (model_key, secs_msg_name)은 유니크이므로 중복 시 DbDuplicateKeyException을 기대합니다.
 */
public record NewTcModelSecsMessage(
        long modelKey,
        String secsMsgName,
        String description,
        String dataIndex
) {
}
