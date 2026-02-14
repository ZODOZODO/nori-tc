package com.nori.tc.common.task.policy;

/**
 * 실패 컨텍스트를 DLQ 표준 레코드로 변환하는 팩토리 계약입니다.
 */
@FunctionalInterface
public interface DlqRecordFactory {

    /**
     * 실패 컨텍스트를 기반으로 DLQ 레코드를 생성합니다.
     *
     * @param context 실패 컨텍스트
     * @param finalCategory 최종 확정 실패 카테고리
     * @return 생성된 DLQ 레코드
     */
    DlqRecord create(TaskFailureContext context, TaskFailureCategory finalCategory);
}

