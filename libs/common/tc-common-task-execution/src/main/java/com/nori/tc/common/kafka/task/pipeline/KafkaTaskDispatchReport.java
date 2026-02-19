package com.nori.tc.common.kafka.task.pipeline;

/**
 * 단일 Kafka task 요청 처리 결과를 요약한 리포트입니다.
 *
 * @param result 최종 처리 결과
 * @param replyEventType 최종 응답 이벤트 타입
 * @param duplicateSkipped traceId 중복으로 처리 본문을 건너뛰었는지 여부
 */
public record KafkaTaskDispatchReport(
        KafkaTaskResult result,
        String replyEventType,
        boolean duplicateSkipped
) {
}


