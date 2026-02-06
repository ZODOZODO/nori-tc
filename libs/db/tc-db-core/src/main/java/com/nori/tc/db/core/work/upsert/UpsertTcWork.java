package com.nori.tc.db.core.work.upsert;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.work.TcWorkState;

/**
 * tc_work upsert 입력(Command).
 *
 * <p>
 * - work_key가 있으면 해당 PK 기반으로 갱신합니다.
 * - work_key가 없으면 (eqp_key, work_id) 유니크 키 기준으로
 *   존재 여부를 확인한 뒤 갱신/생성을 수행합니다.
 * </p>
 *
 * <p>
 * 주의:
 * - created_at/updated_at은 DB(또는 구현체)에서 현재 시각으로 갱신되도록 처리하는 것을 권장합니다.
 * </p>
 */
public record UpsertTcWork(
        Long workKey,
        long eqpKey,
        String workId,
        String operatorId,
        Integer stepSeq,
        TcWorkState workState,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String mesMessage
) {
}
