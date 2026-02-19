package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import com.nori.tc.common.kafka.task.pipeline.DefaultKafkaTaskPipeline;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskDeduplicationStore;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskDlqReporter;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskMessageAccessor;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskProcessorRegistry;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskReplyPublisher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway UI Task 공통 실행 파이프라인을 조립하는 설정 클래스입니다.
 *
 * <p>기존 게이트웨이 전용 분산 로직을
 * {@code tc-common-task-execution} 모듈의 공통 파이프라인으로 통합하여,
 * 앱 간 동일한 처리 규칙(재시도, 중복제거, 응답 발행, DLQ)을 유지합니다.</p>
 */
@Configuration
public class GatewayUiTaskPipelineConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskPipelineConfiguration.class);

    /**
     * Gateway UI Task 처리용 공통 파이프라인 빈을 생성합니다.
     *
     * <p>주의: 설정 프로퍼티의 retry 값은 "재시도 횟수" 기준입니다.
     * {@link FixedRetryPolicy}는 "총 시도 횟수(maxAttempts)"를 받으므로
     * 내부적으로 +1 보정하여 전달합니다.</p>
     *
     * @param accessor 메시지 필드 접근자
     * @param registry UI Task 처리기 레지스트리
     * @param replyPublisher REP 응답 발행기
     * @param dlqReporter DLQ 보고기
     * @param deduplicationStore traceId 중복 저장소
     * @param policyProperties UI Task 정책 프로퍼티
     * @return 공통 UI Task 실행 파이프라인
     */
    @Bean
    public DefaultKafkaTaskPipeline<KafkaUiTaskMessage> gatewayUiTaskPipeline(
            final KafkaTaskMessageAccessor<KafkaUiTaskMessage> accessor,
            final KafkaTaskProcessorRegistry<KafkaUiTaskMessage> registry,
            final KafkaTaskReplyPublisher<KafkaUiTaskMessage> replyPublisher,
            final KafkaTaskDlqReporter<KafkaUiTaskMessage> dlqReporter,
            final KafkaTaskDeduplicationStore deduplicationStore,
            final GatewayUiTaskPolicyProperties policyProperties
    ) {
        log.info("Gateway UI Task 공통 파이프라인을 초기화합니다.");
        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway UI Task 파이프라인 정책: taskRetryMax={}, taskRetryBackoffMs={}, replyRetryMax={}, replyRetryBackoffMs={}, duplicateTraceTtlMs={}",
                    policyProperties.getTaskRetryMax(),
                    policyProperties.getTaskRetryBackoffMs(),
                    policyProperties.getReplyPublishRetryMax(),
                    policyProperties.getReplyPublishRetryBackoffMs(),
                    policyProperties.getDuplicateTraceTtlMs()
            );
        }

        return new DefaultKafkaTaskPipeline<>(
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
