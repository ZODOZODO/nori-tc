package com.nori.tc.db.domain.work;

import java.time.OffsetDateTime;

/**
 * tc_work_param 테이블 1행을 표현하는 순수 DTO.
 *
 * <p>
 * [테이블 특징]
 * - 작업(work) 단위의 파라미터 이름/값을 저장한다.
 * - 동일한 work_key 안에서는 param_name이 유니크해야 한다.
 * - updated_at은 DB에서 자동 갱신된다.
 * </p>
 *
 * <p>
 * [키/제약]
 * - PK: work_param_key (IDENTITY)
 * - FK: work_key -> tc_work.work_key ON DELETE CASCADE
 * - UK: (work_key, param_name)
 * - IDX: work_key (조회 최적화)
 * </p>
 */
public record TcWorkParam(
        long workParamKey,
        long workKey,
        String paramName,
        String paramValue,
        OffsetDateTime updatedAt
) {
}
