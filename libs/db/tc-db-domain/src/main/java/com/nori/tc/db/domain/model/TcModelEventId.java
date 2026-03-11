package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * tc_model_eventid 테이블 1행에 대응하는 순수 DTO.
 *
 * 용도:
 * - 모델(model)별로 수집 대상 event_id 목록을 관리합니다.
 * - model_version_key + event_id 조합이 유니크합니다.
 *
 * 컬럼 설명:
 * - eventKey  : PK (IDENTITY)
 * - modelVersionKey  : tc_model_version.model_version_key FK
 * - eventId   : 장비/모델에서 사용하는 이벤트 ID (varchar(100))
 * - reportId  : 리포트 ID (varchar(1000), nullable)
 * - enabled   : 활성 여부 (default false)
 * - updatedAt : 수정 시각 (timestamptz)
 */
public record TcModelEventId(
        long eventKey,
        long modelVersionKey,
        String eventId,
        String reportId,
        boolean enabled,
        String description,
        OffsetDateTime updatedAt
) {
}
