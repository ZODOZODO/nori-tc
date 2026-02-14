package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import com.nori.tc.common.ui.task.pipeline.DefaultUiTaskPipeline;
import com.nori.tc.common.ui.task.pipeline.UiTaskDeduplicationStore;
import com.nori.tc.common.ui.task.pipeline.UiTaskDlqReporter;
import com.nori.tc.common.ui.task.pipeline.UiTaskMessageAccessor;
import com.nori.tc.common.ui.task.pipeline.UiTaskProcessorRegistry;
import com.nori.tc.common.ui.task.pipeline.UiTaskReplyPublisher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway UI task 공통 파이프라인 구성입니다.
 *
 * <p>기존 gateway 전용 디스패처의 재시도/중복/REP/DLQ 흐름을
 * {@code tc-common-ui-task-pipeline}으로 통합해 앱 간 재사용성을 높입니다.</p>
 */
@Configuration
public class GatewayUiTaskPipelineConfiguration {

    /**
     * Gateway UI task 파이프라인 빈을 생성합니다.
     *
     * <p>주의: gateway 설정의 retry 값은 "재시도 횟수" 의미이므로,
     * 공통 FixedRetryPolicy(maxAttempts=총 시도 횟수)에 맞추기 위해 +1 보정합니다.</p>
     *
     * @param accessor 메시지 필드 접근자
     * @param registry UI task 처리기 레지스트리
     * @param replyPublisher REP 발행기
     * @param dlqReporter DLQ 리포터
     * @param deduplicationStore traceId 중복 저장소
     * @param policyProperties gateway UI task 정책
     * @return 공통 UI task 파이프라인
     */
    @Bean
    public DefaultUiTaskPipeline<KafkaUiTaskMessage> gatewayUiTaskPipeline(
            final UiTaskMessageAccessor<KafkaUiTaskMessage> accessor,
            final UiTaskProcessorRegistry<KafkaUiTaskMessage> registry,
            final UiTaskReplyPublisher<KafkaUiTaskMessage> replyPublisher,
            final UiTaskDlqReporter<KafkaUiTaskMessage> dlqReporter,
            final UiTaskDeduplicationStore deduplicationStore,
            final GatewayUiTaskPolicyProperties policyProperties
    ) {
        return new DefaultUiTaskPipeline<>(
                accessor,
                registry,
                replyPublisher,
                dlqReporter,
                deduplicationStore,
                new FixedRetryPolicy(
                        retryToMaxAttempts(policyProperties.getTaskRetryMax()),
                        policyProperties.getTaskRetryBackoffMs()
                ),
                new FixedRetryPolicy(
                        retryToMaxAttempts(policyProperties.getReplyPublishRetryMax()),
                        policyProperties.getReplyPublishRetryBackoffMs()
                ),
                policyProperties.getDuplicateTraceTtlMs(),
                System::currentTimeMillis
        );
    }

    /**
     * "재시도 횟수"를 "총 시도 횟수(maxAttempts)"로 변환합니다.
     *
     * @param retryCount 재시도 횟수(0 이상)
     * @return 총 시도 횟수(최소 1)
     */
    private static int retryToMaxAttempts(final int retryCount) {
        return Math.max(1, retryCount + 1);
    }
}
