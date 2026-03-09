package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.model.ModelStatus;

/**
 * tc_model_version 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>모델 원장(tc_model)과 1:N 관계를 가지며, 버전 상태/감사 컬럼을 담당합니다.</p>
 */
public record TcModelVersion(
        long modelVersionKey,
        long modelKey,
        String modelVersion,
        ModelStatus status,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
