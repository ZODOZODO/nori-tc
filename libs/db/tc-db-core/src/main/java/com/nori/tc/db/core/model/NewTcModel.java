package com.nori.tc.db.core.model;

import com.nori.tc.db.domain.common.ModelStatus;
import com.nori.tc.db.domain.common.ProtocolType;

/**
 * tc_model 생성 입력(Command)
 *
 * - model_key, created_at, updated_at은 DB에서 생성/갱신되므로 입력에서 제외합니다.
 * - (model_name, model_version)은 유니크이므로 중복 시 DbDuplicateKeyException을 기대합니다.
 * - created_by/updated_by는 null이면 DB 기본값(SYSTEM)을 사용하도록 구현체에서 보정할 수 있습니다.
 */
public record NewTcModel(
        String modelName,
        String modelVersion,
        ProtocolType commInterface,
        ModelStatus status,
        String maker,
        String createdBy,
        String updatedBy
) {
}
