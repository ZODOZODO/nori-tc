package com.nori.tc.db.domain.common.work;

/**
 * 작업(Control Job) 상태 코드 (tc_work_controljob.controljob_state).
 *
 * <p>
 * DB CHECK 제약:
 * - CREATED, QUEUED, RUNNING, PAUSED, COMPLETED, ABORTED, FAILED 값만 허용합니다.
 * </p>
 *
 * <p>
 * 설계 메모:
 * - 상태 전이는 상위 서비스 계층에서 엄격히 관리합니다.
 * - 본 enum은 DB 값과 1:1 매핑되는 코드 집합으로만 유지합니다.
 * </p>
 */
public enum ControlJobState {

    /**
     * 작업이 생성된 직후 상태
     */
    CREATED,

    /**
     * 스케줄러/큐에 적재된 상태
     */
    QUEUED,

    /**
     * 실행 중 상태
     */
    RUNNING,

    /**
     * 일시 정지 상태
     */
    PAUSED,

    /**
     * 정상 완료 상태
     */
    COMPLETED,

    /**
     * 사용자/시스템에 의해 중단된 상태
     */
    ABORTED,

    /**
     * 실패 상태
     */
    FAILED
}
