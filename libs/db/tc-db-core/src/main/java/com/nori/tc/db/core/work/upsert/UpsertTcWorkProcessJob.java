package com.nori.tc.db.core.work.upsert;

import com.nori.tc.db.domain.common.work.TcWorkProcessJobState;

/**
 * tc_work_processjob upsert 입력(Command).
 *
 * <p>
 * - processJobKey가 있으면 해당 PK 기준으로 갱신을 시도한다.
 * - processJobKey가 없으면 (control_job_key, processjob_id) 유니크 키를 기준으로
 *   존재 여부를 확인한 뒤 갱신/생성을 수행한다.
 * </p>
 *
 * <p>
 * 주의:
 * - created_at/updated_at은 DB/JPA 라이프사이클에서 관리하도록 둔다.
 * - processjob_state는 DB 체크 제약과 동일한 enum을 사용한다.
 * </p>
 */
public record UpsertTcWorkProcessJob(
        Long processJobKey,
        long controlJobKey,
        String processjobId,
        TcWorkProcessJobState processjobState,
        String recipeId
) {
}
