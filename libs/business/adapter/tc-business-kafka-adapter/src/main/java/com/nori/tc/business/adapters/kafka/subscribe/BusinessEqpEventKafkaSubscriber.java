package com.nori.tc.business.adapters.kafka.subscribe;

import com.nori.tc.business.core.logging.BusinessLogContext;
import com.nori.tc.business.core.runtime.BusinessTaskIngressPort;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code tc.eqp.events} 수신 Subscriber입니다.
 *
 * <p>역할:
 * 1) Kafka 원문(JSON)을 Business inbound record로 변환
 * 2) Business 런타임 ingress 포트로 전달</p>
 */
@Component
public class BusinessEqpEventKafkaSubscriber {

    private static final Logger log = LoggerFactory.getLogger(BusinessEqpEventKafkaSubscriber.class);

    private final BusinessTaskIngressPort ingressPort;
    private final BusinessKafkaInboundRecordMapper recordMapper;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param ingressPort Business 런타임 ingress 포트
     * @param recordMapper Kafka -> Business record 변환기
     */
    public BusinessEqpEventKafkaSubscriber(
            final BusinessTaskIngressPort ingressPort,
            final BusinessKafkaInboundRecordMapper recordMapper
    ) {
        this.ingressPort = Objects.requireNonNull(ingressPort, "ingressPort is null");
        this.recordMapper = Objects.requireNonNull(recordMapper, "recordMapper is null");
    }

    /**
     * EQP 이벤트를 수신하여 런타임 큐에 적재합니다.
     *
     * @param record Kafka consumer record
     * @throws Exception 파싱/적재 실패
     */
    @KafkaListener(
            topics = "${tc.business.core.kafka.eqp-events-topic}",
            properties = {
                    "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            }
    )
    /**
     * onMessage 기능을 수행합니다.
     *
     * @param record 입력 값
     */

    public void onMessage(final ConsumerRecord<String, String> record) throws Exception {
        final BusinessInboundRecord inboundRecord = recordMapper.map(record, BusinessMessageType.EQP);
        try (BusinessLogContext ignored = BusinessLogContext.withEqpId(inboundRecord.eqpId())) {
            final boolean accepted = ingressPort.submit(inboundRecord);
            if (!accepted) {
                throw new IllegalStateException(
                        "Runtime queue overflow while ingesting EQP event. topic="
                                + record.topic()
                                + ", partition="
                                + record.partition()
                                + ", offset="
                                + record.offset()
                );
            }

            if (log.isDebugEnabled()) {
                log.debug("EQP event ingested. topic={}, partition={}, offset={}, eqpId={}, eventType={}",
                        inboundRecord.topic(),
                        inboundRecord.partition(),
                        inboundRecord.offset(),
                        inboundRecord.eqpId(),
                        inboundRecord.messageName());
            }
        }
    }
}
