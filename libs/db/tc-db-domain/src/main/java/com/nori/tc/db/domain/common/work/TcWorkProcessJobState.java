package com.nori.tc.db.domain.common.work;

/**
 * tc_work_processjob.processjob_state 상태 값 정의.
 *
 * <p>
 * DB 체크 제약(ck_tc_work_processjob_state)을 코드 레벨에서도 동일하게 맞춘다.
 * </p>
 *
 * <ul>
 *     <li>CREATED: 프로세스 작업이 생성됨</li>
 *     <li>QUEUED: 실행 대기열에 들어감</li>
 *     <li>RUNNING: 실행 중</li>
 *     <li>PAUSED: 일시 정지됨</li>
 *     <li>COMPLETED: 정상 완료</li>
 *     <li>ABORTED: 중단됨</li>
 *     <li>FAILED: 실패 종료</li>
 * </ul>
 */
public enum TcWorkProcessJobState {
    CREATED,
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    ABORTED,
    FAILED
}
