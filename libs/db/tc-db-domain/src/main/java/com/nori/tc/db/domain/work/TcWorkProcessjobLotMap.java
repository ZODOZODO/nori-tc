package com.nori.tc.db.domain.work;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.work.TcWorkProcessjobLotMapOrder;

/**
 * tc_work_processjob_lot_map 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * PK:
 * - pj_lot_map_key : bigint identity (PK)
 * </p>
 *
 * <p>
 * Unique:
 * - (process_job_key, work_lot_key)
 * </p>
 *
 * <p>
 * 주요 컬럼:
 * - process_job_key : 공정 작업(Work ProcessJob) 키 (필수)
 * - work_lot_key    : 작업 LOT 키 (필수)
 * - map_role        : 매핑 역할(옵션, 최대 20자)
 * - map_order       : 매핑 순서/방향(FORWARD/REVERSE, 옵션)
 * - created_at      : 생성 시각 (DB 자동)
 * - updated_at      : 갱신 시각 (DB 자동)
 * </p>
 */
public record TcWorkProcessjobLotMap(
        long pjLotMapKey,
        long processJobKey,
        long workLotKey,
        String mapRole,
        TcWorkProcessjobLotMapOrder mapOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
