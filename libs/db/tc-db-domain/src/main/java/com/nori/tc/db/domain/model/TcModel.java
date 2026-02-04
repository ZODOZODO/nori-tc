package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.ModelStatus;
import com.nori.tc.db.domain.common.ProtocolType;

/**
 * tc_model 테이블 1행에 대응하는 순수 DTO.
 *
 * - model_key: DB에서 IDENTITY로 생성됨 (조회 결과에는 항상 존재)
 * - (model_name, model_version) 유니크
 */
public record TcModel(
        long modelKey,
        String modelName,
        String modelVersion,
        ProtocolType protocolType,
        ModelStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
