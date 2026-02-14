package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.business.core.runtime.BusinessTaskIngressPort;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code tc.ui.events} 구독 listener입니다.
 *
 * <p>역할:
 * - Kafka UI 메시지를 runtime ingress 포맷으로 변환
 * - eqpId 기준 mailbox 순차 처리 경로로 전달
 * - 런타임 큐 포화 시 예외를 발생시켜 재처리를 유도</p>
 *
 * <p>주의:
 * - 이 listener는 {@code tc.business.core.ui-task.kafka-listener-enabled=true}
 * 일 때만 활성화됩니다.</p>
 */
@Component
@ConditionalOnProperty(
        name = "tc.business.core.ui-task.kafka-listener-enabled",
        havingValue = "true"
)
public class BusinessUiTaskKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskKafkaListener.class);

    private final BusinessTaskIngressPort taskIngressPort;
    private final ObjectMapper objectMapper;

    /**
     * listener 의존성을 주입받습니다.
     */
    public BusinessUiTaskKafkaListener(
            final BusinessTaskIngressPort taskIngressPort,
            final ObjectMapper objectMapper
    ) {
        this.taskIngressPort = Objects.requireNonNull(taskIngressPort, "taskIngressPort is null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
    }

    /**
     * UI Kafka 레코드를 수신하여 runtime ingress에 전달합니다.
     *
     * @param record Kafka consumer record
     */
    @KafkaListener(topics = "${tc.business.core.kafka.ui-events-topic}")
    public void onMessage(final ConsumerRecord<String, KafkaUiTaskMessage> record) {
        final KafkaUiTaskMessage message = record.value();
        if (message == null) {
            log.warn("UI Kafka record ignored: value is null. topic={}, partition={}, offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset());
            return;
        }

        final String eqpId = message.data() == null ? null : message.data().eqpId();
        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize UI Kafka message to runtime payload", ex);
        }

        final String payloadRef = record.topic() + ":" + record.partition() + ":" + record.offset();
        final BusinessInboundRecord inboundRecord = new BusinessInboundRecord(
                record.topic(),
                record.partition(),
                record.offset(),
                eqpId,
                BusinessMessageType.UI,
                eventType,
                payloadRef,
                payload
        );

        final boolean accepted = taskIngressPort.submit(inboundRecord);
        if (!accepted) {
            throw new IllegalStateException(
                    "Runtime queue overflow while ingesting UI Kafka record. topic="
                            + record.topic()
                            + ", partition="
                            + record.partition()
                            + ", offset="
                            + record.offset()
            );
        }

        if (log.isDebugEnabled()) {
            log.debug("UI Kafka record ingested to runtime. topic={}, partition={}, offset={}, eqpId={}, eventType={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    eqpId,
                    eventType);
        }
    }
}


