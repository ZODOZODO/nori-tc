package com.nori.tc.db.core.model;

import com.nori.tc.db.domain.common.ModelStatus;
import com.nori.tc.db.domain.common.ProtocolType;

/**
 * tc_model 갱신 입력(Command)
 *
 * - model_key로 대상 식별(대리키 기반)
 * - 변경 가능 필드만 포함합니다.
 *
 * 주의:
 * - created_at은 변경 대상이 아닙니다.
 * - updated_at은 DB(또는 구현체)에서 현재 시각으로 갱신되도록 처리하는 것을 권장합니다.
 */
public record UpdateTcModel(
        long modelKey,
        String modelName,
        String modelVersion,
        ProtocolType protocolType,
        ModelStatus status
) {
}
