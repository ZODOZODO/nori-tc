package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * tc_model_secs_message 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - secs_msg_key (PK, identity)
 * - model_version_key (FK -> tc_model_version.model_version_key, ON DELETE CASCADE)
 *
 * 주요 컬럼:
 * - secs_msg_name : 모델 내 유니크 메시지 이름 (varchar(1000), model_version_key + secs_msg_name 유니크)
 * - description   : 메시지 설명 (nullable)
 * - data_index    : 데이터 인덱스/식별자 (nullable)
 */
public record TcModelSecsMessage(
        long secsMsgKey,
        long modelVersionKey,
        String secsMsgName,
        String description,
        String dataIndex,
        OffsetDateTime updatedAt
) {
}
