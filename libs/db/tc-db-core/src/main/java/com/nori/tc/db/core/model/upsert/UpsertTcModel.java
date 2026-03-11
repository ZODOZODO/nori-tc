package com.nori.tc.db.core.model.upsert;

import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;

/**
 * tc_model 업서트 입력 Command입니다.
 *
 * <p>
 * 호환성 규칙(중요):
 * - 필드 이름은 {@code modelKey}이지만, 실제 의미는 "tc_model.model_key"가 아닙니다.
 * - 기존 호출자와의 호환을 위해 "tc_model_version.model_version_key"로 해석합니다.
 * - 즉, {@code modelKey > 0}이면 특정 모델 버전 행 업데이트 경로로 처리됩니다.
 * </p>
 *
 * <p>
 * 저장 규칙:
 * - modelName/modelVersion은 자연키 조합처럼 동작하며,
 *   insert 경로에서는 ({@code model_name}, {@code model_version}) 기준 upsert 흐름을 탑니다.
 * - createdBy/updatedBy는 감사 컬럼 용도입니다.
 * </p>
 */
public record UpsertTcModel(
        Long modelKey,
        String modelName,
        String parentModel,
        String modelVersion,
        ProtocolType commInterface,
        ModelStatus status,
        String description,
        String maker,
        String createdBy,
        String updatedBy
) {

    public UpsertTcModel {
        if (modelKey != null && modelKey < 0) {
            throw new IllegalArgumentException("modelKey compatibility value must be >= 0");
        }
        ModelFieldLengthValidator.requireTextWithMax(
                modelName,
                "modelName",
                ModelFieldLengthValidator.MODEL_NAME_MAX_LENGTH
        );
        ModelFieldLengthValidator.validateOptionalTextMax(
                parentModel,
                "parentModel",
                ModelFieldLengthValidator.MODEL_NAME_MAX_LENGTH
        );
        ModelFieldLengthValidator.requireTextWithMax(
                modelVersion,
                "modelVersion",
                ModelFieldLengthValidator.MODEL_VERSION_MAX_LENGTH
        );
        if (commInterface == null) {
            throw new IllegalArgumentException("commInterface must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
