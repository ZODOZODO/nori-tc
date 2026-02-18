package com.nori.tc.business.core.runtime;

/**
 * Business Runtime의 표준 처리 결과(disposition)입니다.
 *
 * <p>Phase 4 운영 강건화 목표에 따라, 런타임의 최종 처리 상태를
 * 운영 관측/대시보드에서 동일한 축으로 집계하기 위해 사용합니다.</p>
 *
 * <p>표준 값:</p>
 * <p>1) ACCEPTED: 정상 처리 완료(커밋 가능)</p>
 * <p>2) RETRY: 재시도 예약됨(아직 커밋 불가)</p>
 * <p>3) DLQ: DLQ로 이관되어 현재 소비 흐름에서 종료</p>
 * <p>4) REJECTED: 재시도/DLQ 없이 거부 또는 실패 종료</p>
 */
public enum BusinessRuntimeDisposition {
    ACCEPTED,
    RETRY,
    DLQ,
    REJECTED
}

