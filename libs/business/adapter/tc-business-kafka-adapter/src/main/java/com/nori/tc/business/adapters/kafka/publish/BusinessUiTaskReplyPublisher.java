package com.nori.tc.business.adapters.kafka.publish;

import com.nori.tc.business.adapters.kafka.config.BusinessUiTaskPolicyProperties;
import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskReplyPublisher;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaHeaderSupport;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskReplyMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * UI REP 발행기입니다.
 *
 * <p>요청 traceId를 그대로 유지한 응답 메시지를 구성하여
 * {@code tc.ui.commands}로 발행합니다.</p>
 */
@Component
public class BusinessUiTaskReplyPublisher implements KafkaTaskReplyPublisher<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskReplyPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BusinessCoreRuntimeProperties runtimeProperties;
    private final BusinessUiTaskPolicyProperties policyProperties;

    /**
     * UI REP 발행기 의존성을 주입받습니다.
     */
    public BusinessUiTaskReplyPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final BusinessCoreRuntimeProperties runtimeProperties,
            final BusinessUiTaskPolicyProperties policyProperties
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");
        this.policyProperties = Objects.requireNonNull(policyProperties, "policyProperties is null");
    }

    @Override
    public void publishResult(
            final KafkaUiTaskMessage request,
            final String replyEventType,
            final KafkaTaskResult result
    ) throws Exception {
        Objects.requireNonNull(request, "request is null");
        Objects.requireNonNull(result, "result is null");

        final String topic = runtimeProperties.getKafka().getUiCommandsTopic();
        final String eqpId = request.data().eqpId();
        final String traceId = request.metadata().traceId();

        final KafkaUiTaskMessage.KafkaUiTaskMetadata metadata = new KafkaUiTaskMessage.KafkaUiTaskMetadata(
                normalizeRequired(replyEventType, "replyEventType"),
                Instant.now().toString(),
                policyProperties.getSource(),
                traceId
        );
        final KafkaUiTaskReplyMessage.KafkaUiTaskReplyData data = new KafkaUiTaskReplyMessage.KafkaUiTaskReplyData(
                eqpId,
                request.data().interfaceType(),
                result.status().name(),
                normalizeNullable(result.errorMessage()),
                normalizeNullable(result.errorCode())
        );

        final KafkaUiTaskReplyMessage payload = new KafkaUiTaskReplyMessage(metadata, data);
        final ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, eqpId, payload);
        KafkaHeaderSupport.addTracingHeaders(
                producerRecord,
                metadata.traceId(),
                metadata.eventType(),
                metadata.source()
        );

        if (log.isDebugEnabled()) {
            log.debug("Publishing UI REP. topic={}, eventType={}, eqpId={}, traceId={}, status={}",
                    topic,
                    replyEventType,
                    eqpId,
                    traceId,
                    result.status());
        }

        try {
            kafkaTemplate.send(producerRecord).get();
        } catch (Exception ex) {
            log.error("UI REP publish failed. topic={}, eventType={}, eqpId={}, traceId={}",
                    topic,
                    replyEventType,
                    eqpId,
                    traceId,
                    ex);
            throw ex;
        }

        if (log.isDebugEnabled()) {
            log.debug("UI REP published. topic={}, eventType={}, eqpId={}, traceId={}, status={}",
                    topic,
                    replyEventType,
                    eqpId,
                    traceId,
                    result.status());
        }
    }

    private static String normalizeRequired(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeNullable(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}



