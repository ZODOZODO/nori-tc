package com.nori.tc.common.task.execution.policy.dlq;

import com.nori.tc.common.task.execution.policy.types.DlqRecord;
import com.nori.tc.common.task.execution.policy.types.TaskFailureCategory;
import com.nori.tc.common.task.execution.policy.types.TaskFailureContext;

/**
 * 실패 컨텍스트를 DLQ 레코드로 변환하는 팩토리 인터페이스입니다.
 *
 * <p>구현체는 운영 분석에 필요한 최소 필드(토픽/오프셋/eqpId/카테고리)를
 * 누락하지 않도록 레코드를 구성해야 합니다.</p>
 */
@FunctionalInterface
public interface DlqRecordFactory {

    /**
     * 실패 컨텍스트와 최종 카테고리를 기반으로 DLQ 레코드를 생성합니다.
     *
     * @param context 실패 컨텍스트
     * @param finalCategory 최종 실패 카테고리
     * @return DLQ 저장용 레코드
     */
    DlqRecord create(TaskFailureContext context, TaskFailureCategory finalCategory);
}
