package com.nori.tc.business.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.adapters.kafka.config.BusinessUiTaskPolicyProperties;
import com.nori.tc.common.consumer.runtime.FixedRetryPolicy;
import com.nori.tc.common.task.execution.pipeline.runtime.KafkaTaskExecutionPipeline;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDeduplicationStore;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDlqReporter;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskMessageAccessor;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskProcessorRegistry;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskReplyPublisher;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * BusinessUiTaskPipelineConfiguration 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */
@Configuration
@EnableConfigurationProperties(BusinessUiTaskPolicyProperties.class)
public class BusinessUiTaskPipelineConfiguration {

    /**
     * businessUiObjectMapper 기능을 수행합니다.
     *
     * @return 처리 결과
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper businessUiObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    @Bean
    public KafkaTaskExecutionPipeline<KafkaUiTaskMessage> businessUiTaskPipeline(
            final KafkaTaskMessageAccessor<KafkaUiTaskMessage> accessor,
            final KafkaTaskProcessorRegistry<KafkaUiTaskMessage> registry,
            final KafkaTaskReplyPublisher<KafkaUiTaskMessage> replyPublisher,
            final KafkaTaskDlqReporter<KafkaUiTaskMessage> dlqReporter,
            final KafkaTaskDeduplicationStore deduplicationStore,
            final BusinessUiTaskPolicyProperties policyProperties
    ) {
        return new KafkaTaskExecutionPipeline<>(
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

