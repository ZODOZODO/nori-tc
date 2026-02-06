package com.nori.tc.db.domain.common.work;

/**
 * tc_work.work_state 컬럼에 저장되는 작업 상태.
 *
 * <p>
 * - DB CHECK 제약과 1:1로 매핑되는 Enum 값입니다.
 * - 저장 시 enum 이름이 그대로 문자열로 저장됩니다.
 * </p>
 *
 * <p>
 * [허용 값]
 * - QUEUED    : 작업 대기
 * - RUNNING   : 작업 수행 중
 * - COMPLETED : 정상 완료
 * - ABORTED   : 중단/비정상 종료
 * </p>
 */
public enum TcWorkState {
    QUEUED,
    RUNNING,
    COMPLETED,
    ABORTED
}
