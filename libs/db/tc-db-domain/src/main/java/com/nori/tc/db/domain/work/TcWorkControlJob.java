package com.nori.tc.db.domain.work;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.work.ControlJobState;

/**
 * tc_work_controljob 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * PK:
 * - control_job_key (IDENTITY)
 * </p>
 *
 * <p>
 * FK:
 * - work_key (tc_work.work_key) ON DELETE CASCADE
 * </p>
 *
 * <p>
 * Unique:
 * - (work_key, controljob_id)
 * </p>
 *
 * <p>
 * Index:
 * - (controljob_id)
 * - (work_key)
 * </p>
 *
 * <p>
 * Check:
 * - controljob_state IN ('CREATED', 'QUEUED', 'RUNNING', 'PAUSED', 'COMPLETED', 'ABORTED', 'FAILED')
 * </p>
 *
 * nullable 컬럼은 Java에서 null 허용 타입으로 둡니다.
 */
public record TcWorkControlJob(
        long controlJobKey,
        long workKey,
        String controljobId,
        ControlJobState controljobState,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
