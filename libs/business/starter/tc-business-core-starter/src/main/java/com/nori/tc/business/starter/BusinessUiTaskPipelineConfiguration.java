package com.nori.tc.business.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.adapters.kafka.config.BusinessUiTaskPolicyProperties;
import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import com.nori.tc.common.kafka.task.pipeline.DefaultKafkaTaskPipeline;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskDeduplicationStore;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskDlqReporter;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskMessageAccessor;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskProcessorRegistry;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskReplyPublisher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Business UI task 공통 파이프라인 구성 클래스입니다.
 */
@Configuration
@EnableConfigurationProperties(BusinessUiTaskPolicyProperties.class)
public class BusinessUiTaskPipelineConfiguration {

    /**
     * ObjectMapper 빈이 없는 환경에서도 UI payload 파싱이 가능하도록
     * 최소 ObjectMapper를 제공합니다.
     *
     * @return ObjectMapper 빈
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper businessUiObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * {@link DefaultKafkaTaskPipeline} 빈을 구성합니다.
     *
     * @param accessor 메시지 필드 접근자
     * @param registry eventType 처리기 레지스트리
     * @param replyPublisher REP 발행기
     * @param dlqReporter 파이프라인 DLQ 리포터
     * @param deduplicationStore traceId dedup 저장소
     * @param policyProperties UI task 정책 프로퍼티
     * @return 공통 UI task 파이프라인
     */
    @Bean
    public DefaultKafkaTaskPipeline<KafkaUiTaskMessage> businessUiTaskPipeline(
            final KafkaTaskMessageAccessor<KafkaUiTaskMessage> accessor,
            final KafkaTaskProcessorRegistry<KafkaUiTaskMessage> registry,
            final KafkaTaskReplyPublisher<KafkaUiTaskMessage> replyPublisher,
            final KafkaTaskDlqReporter<KafkaUiTaskMessage> dlqReporter,
            final KafkaTaskDeduplicationStore deduplicationStore,
            final BusinessUiTaskPolicyProperties policyProperties
    ) {
        return new DefaultKafkaTaskPipeline<>(
                accessor,
                registry,
                replyPublisher,
                dlqReporter,
                deduplicationStore,
                new FixedRetryPolicy(
                        policyProperties.getTaskRetryMaxAttempts(),
                        policyProperties.getTaskRetryBackoffMs()
                ),
                new FixedRetryPolicy(
                        policyProperties.getReplyRetryMaxAttempts(),
                        policyProperties.getReplyRetryBackoffMs()
                ),
                policyProperties.getDuplicateTraceTtlMs(),
                System::currentTimeMillis
        );
    }
}


