package com.nori.tc.comm.adapters.kafka.ui;

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
 * Gateway UI Task 파이프라인 관련 Bean을 조립하는 설정 클래스입니다.
 *
 * <p>구성 내용:</p>
 * <p>1) UI 메시지 필드 접근자(eventType/traceId/eqpId)를 제공합니다.</p>
 * <p>2) Task 처리 재시도 정책과 Reply 발행 재시도 정책을 생성합니다.</p>
 * <p>3) 공통 {@link DefaultKafkaTaskPipeline} 인스턴스를 생성합니다.</p>
 */
@Configuration
public class GatewayUiTaskPipelineConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskPipelineConfiguration.class);

    /**
     * UI Task 메시지 접근자를 Bean으로 등록합니다.
     *
     * @return UI Task용 메시지 접근자
     */
    @Bean
    public KafkaTaskMessageAccessor<KafkaUiTaskMessage> gatewayUiTaskMessageAccessor() {
        return new KafkaTaskMessageAccessor<>() {
            @Override
            public String eventType(final KafkaUiTaskMessage request) {
                if (request == null || request.metadata() == null) {
                    return null;
                }
                return request.metadata().eventType();
            }

            @Override
            public String traceId(final KafkaUiTaskMessage request) {
                if (request == null || request.metadata() == null) {
                    return null;
                }
                return request.metadata().traceId();
            }

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
     * UI Task 공통 파이프라인을 생성합니다.
     *
     * <p>주의: properties 의 retry 값은 "재시도 횟수" 개념이며,
     * 실제 정책 객체에서는 "총 시도 횟수(maxAttempts)"로 변환해서 사용합니다.</p>
     *
     * @param accessor 메시지 접근자
     * @param registry eventType 처리기 레지스트리
     * @param replyPublisher 결과 Reply 발행기
     * @param dlqReporter DLQ 리포터
     * @param deduplicationStore traceId 중복 저장소
     * @param policyProperties UI Task 정책 값
     * @return 조립된 UI Task 파이프라인
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
     * @param retryCount 재시도 횟수
     * @return 최소 1 이상인 총 시도 횟수
     */
    private static int retryToMaxAttempts(final int retryCount) {
        return Math.max(1, retryCount + 1);
    }
}
