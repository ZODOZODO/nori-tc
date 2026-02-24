package com.nori.tc.comm.adapters.kafka.ui;

import com.nori.tc.comm.gateway.config.props.GatewayUiTaskPolicyProperties;
import com.nori.tc.common.consumer.runtime.FixedRetryPolicy;
import com.nori.tc.common.task.execution.pipeline.runtime.KafkaTaskExecutionPipeline;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDeduplicationStore;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDlqReporter;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskMessageAccessor;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskProcessorRegistry;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskReplyPublisher;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway UI Task 파이프라인 Bean 조립 설정입니다.
 *
 * <p>핵심 책임:</p>
 * <p>1) UI 메시지 접근자(eventType/traceId/eqpId) 제공</p>
 * <p>2) 처리기 레지스트리/응답 발행기/DLQ 리포터/중복 저장소 결합</p>
 * <p>3) 공통 {@link KafkaTaskExecutionPipeline} 인스턴스 생성</p>
 */
@Configuration
public class GatewayUiTaskPipelineConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskPipelineConfiguration.class);

    /**
     * UI Task 메시지 접근자 Bean을 생성합니다.
     *
     * @return UI Task 메시지 접근자
     */
    @Bean
    public KafkaTaskMessageAccessor<KafkaUiTaskMessage> gatewayUiTaskMessageAccessor() {
        return new KafkaTaskMessageAccessor<>() {
            /**
             * eventType 기능을 수행합니다.
             *
             * @param request 입력 값
             * @return 처리 결과
             */

            @Override
            public String eventType(final KafkaUiTaskMessage request) {
                if (request == null || request.metadata() == null) {
                    return null;
                }
                return request.metadata().eventType();
            }

            /**
             * traceId 기능을 수행합니다.
             *
             * @param request 입력 값
             * @return 처리 결과
             */

            @Override
            public String traceId(final KafkaUiTaskMessage request) {
                if (request == null || request.metadata() == null) {
                    return null;
                }
                return request.metadata().traceId();
            }

            /**
             * eqpId 기능을 수행합니다.
             *
             * @param request 입력 값
             * @return 처리 결과
             */

            @Override
            public String eqpId(final KafkaUiTaskMessage request) {
                if (request == null || request.data() == null) {
                    return null;
                }
                return request.data().eqpId();
            }
        };
    }

    /**
     * UI Task 공통 실행 파이프라인을 생성합니다.
     *
     * <p>주의:</p>
     * <p>properties의 retry 값은 "재시도 횟수"이고,
     * `FixedRetryPolicy`는 "총 시도 횟수(maxAttempts)"를 받으므로 +1 보정이 필요합니다.</p>
     *
     * @param accessor 메시지 접근자
     * @param registry eventType 처리기 레지스트리
     * @param replyPublisher 처리 결과 Reply 발행기
     * @param dlqReporter 실패 DLQ 리포터
     * @param deduplicationStore traceId 중복 저장소
     * @param policyProperties UI Task 정책
     * @return 구성된 UI Task 실행 파이프라인
     */
    @Bean
    public KafkaTaskExecutionPipeline<KafkaUiTaskMessage> gatewayUiTaskPipeline(
            final KafkaTaskMessageAccessor<KafkaUiTaskMessage> accessor,
            final KafkaTaskProcessorRegistry<KafkaUiTaskMessage> registry,
            final KafkaTaskReplyPublisher<KafkaUiTaskMessage> replyPublisher,
            final KafkaTaskDlqReporter<KafkaUiTaskMessage> dlqReporter,
            final KafkaTaskDeduplicationStore deduplicationStore,
            final GatewayUiTaskPolicyProperties policyProperties
    ) {
        log.info("Gateway UI Task pipeline initializing.");
        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway UI Task policy loaded. taskRetryMax={}, taskRetryBackoffMs={}, replyRetryMax={}, replyRetryBackoffMs={}, duplicateTraceTtlMs={}",
                    policyProperties.getTaskRetryMax(),
                    policyProperties.getTaskRetryBackoffMs(),
                    policyProperties.getReplyPublishRetryMax(),
                    policyProperties.getReplyPublishRetryBackoffMs(),
                    policyProperties.getDuplicateTraceTtlMs()
            );
        }

        return new KafkaTaskExecutionPipeline<>(
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
     * 재시도 횟수를 총 시도 횟수(maxAttempts)로 변환합니다.
     *
     * @param retryCount 재시도 횟수
     * @return 최소 1 이상인 총 시도 횟수
     */
    private static int retryToMaxAttempts(final int retryCount) {
        return Math.max(1, retryCount + 1);
    }
}
