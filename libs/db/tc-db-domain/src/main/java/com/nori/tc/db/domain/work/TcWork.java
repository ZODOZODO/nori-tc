package com.nori.tc.db.domain.work;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.work.TcWorkState;

/**
 * tc_work 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * PK:
 * - work_key : bigint identity (PK)
 * </p>
 *
 * <p>
 * FK:
 * - eqp_key -> tc_eqp.eqp_key
 * </p>
 *
 * <p>
 * Unique:
 * - (eqp_key, work_id)
 * </p>
 *
 * <p>
 * 주요 컬럼:
 * - work_id     : 설비 내 작업 식별자 (필수, eqp_key와 함께 유니크)
 * - operator_id : 작업자 식별자 (선택)
 * - step_seq    : 공정/스텝 순번 (0 이상, 선택)
 * - work_state  : 작업 상태 (QUEUED/RUNNING/COMPLETED/ABORTED)
 * - start_time  : 작업 시작 시각 (선택)
 * - end_time    : 작업 종료 시각 (선택)
 * - mes_message : MES 연계 메시지 (선택, TEXT)
 * - created_at  : 생성 시각 (DB 자동)
 * - updated_at  : 갱신 시각 (DB 자동)
 * </p>
 */
public record TcWork(
        long workKey,
        long eqpKey,
        String workId,
        String operatorId,
        Integer stepSeq,
        TcWorkState workState,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String mesMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
