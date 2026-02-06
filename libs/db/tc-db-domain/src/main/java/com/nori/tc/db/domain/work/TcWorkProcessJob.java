package com.nori.tc.db.domain.work;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.work.TcWorkProcessJobState;

/**
 * tc_work_processjob 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * PK:
 * - process_job_key : bigint identity (PK)
 * </p>
 *
 * <p>
 * Unique:
 * - (control_job_key, processjob_id)
 * </p>
 *
 * <p>
 * 주요 컬럼:
 * - control_job_key  : 상위 컨트롤 잡 키 (필수, FK)
 * - processjob_id    : 프로세스 잡 ID (필수)
 * - processjob_state : 프로세스 잡 상태 (필수)
 * - recipe_id        : 레시피 ID (필수)
 * - created_at       : 생성 시각 (DB 자동)
 * - updated_at       : 갱신 시각 (DB 자동)
 * </p>
 */
public record TcWorkProcessJob(
        long processJobKey,
        long controlJobKey,
        String processjobId,
        TcWorkProcessJobState processjobState,
        String recipeId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
