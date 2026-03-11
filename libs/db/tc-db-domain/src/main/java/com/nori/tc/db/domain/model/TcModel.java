package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;

/**
 * tc_model 테이블 1행에 대응하는 순수 DTO.
 *
 * - model_key: DB에서 IDENTITY로 생성됨 (조회 결과에는 항상 존재)
 * - (model_name, model_version) 유니크
 * - comm_interface: SECS/SOCKET
 * - parent_model: root는 null, branch는 부모 model_name
 * - created_by/updated_by: 시스템 계정 또는 호출자 계정
 */
public record TcModel(
        long modelVersionKey,
        long modelKey,
        String modelName,
        String parentModel,
        String modelVersion,
        ProtocolType commInterface,
        ModelStatus status,
        String description,
        String maker,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
