package com.nori.tc.db.core.model.upsert;

import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;

/**
 * tc_model upsert 입력(Command).
 *
 * <p>
 * - model_key가 있으면 해당 PK 기반으로 갱신합니다.
 * - model_key가 없으면 (model_name, model_version) 유니크 키 기준으로
 * 존재 여부를 확인한 뒤 갱신/생성을 수행합니다.
 * </p>
 *
 * <p>
 * 주의:
 * - created_at은 변경 대상이 아닙니다.
 * - updated_at은 DB(또는 구현체)에서 현재 시각으로 갱신되도록 처리하는 것을 권장합니다.
 * - created_by는 신규 생성 시에만 사용하고, 갱신 시에는 기존 값을 유지합니다.
 * </p>
 */
public record UpsertTcModel(
        Long modelKey,
        String modelName,
        String modelVersion,
        ProtocolType commInterface,
        ModelStatus status,
        String maker,
        String createdBy,
        String updatedBy
) {
}