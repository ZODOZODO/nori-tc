package com.nori.tc.db.core.work.upsert;

/**
 * tc_work_param upsert 입력(Command).
 *
 * <p>
 * - Unique(work_key, param_name)를 기준으로 param_value를 갱신한다.
 * - 동일한 이름이 없으면 신규로 생성한다.
 * - work_key/param_name은 Unique Key이므로 변경 대상에서 제외한다.
 * </p>
 */
public record UpsertTcWorkParam(
        long workKey,
        String paramName,
        String paramValue
) {
}
