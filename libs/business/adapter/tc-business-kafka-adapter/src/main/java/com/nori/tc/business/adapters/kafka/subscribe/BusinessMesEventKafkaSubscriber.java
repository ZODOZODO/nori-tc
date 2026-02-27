package com.nori.tc.business.adapters.kafka.subscribe;

import com.nori.tc.business.core.logging.BusinessLogContext;
import com.nori.tc.business.core.logging.BusinessObservationLogger;
import com.nori.tc.business.core.logging.BusinessTraceIdGenerator;
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
 * {@code tc.mes.events} 수신 Subscriber입니다.
 *
 * <p>역할:
 * 1) Kafka 원문(JSON)을 Business inbound record로 변환
 * 2) Business 런타임 ingress 포트로 전달</p>
 */
@Component
public class BusinessMesEventKafkaSubscriber {

    private static final Logger log = LoggerFactory.getLogger(BusinessMesEventKafkaSubscriber.class);

    private final BusinessTaskIngressPort ingressPort;
    private final BusinessKafkaInboundRecordMapper recordMapper;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param ingressPort Business 런타임 ingress 포트
     * @param recordMapper Kafka -> Business record 변환기
     */
    public BusinessMesEventKafkaSubscriber(
            final BusinessTaskIngressPort ingressPort,
            final BusinessKafkaInboundRecordMapper recordMapper
    ) {
        this.ingressPort = Objects.requireNonNull(ingressPort, "ingressPort is null");
        this.recordMapper = Objects.requireNonNull(recordMapper, "recordMapper is null");
    }

    /**
     * MES 이벤트를 수신하여 런타임 큐에 적재합니다.
     *
     * @param record Kafka consumer record
     * @throws Exception 파싱/적재 실패
     */
    @KafkaListener(
            topics = "${tc.business.core.kafka.mes-events-topic}",
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
        final BusinessInboundRecord mappedRecord = recordMapper.map(record, BusinessMessageType.MES);
        /*
         * MES inbound traceId policy:
         * - Kafka correlationId는 메시지 계약/키 검증에 사용합니다.
         * - Business 내부 처리 생명주기 traceId는 구독 직후 신규 발급하여
         *   mailbox enqueue 이전부터 일관되게 추적합니다.
         */
        final BusinessInboundRecord inboundRecord = withTraceId(
                mappedRecord,
                BusinessTraceIdGenerator.newTraceId()
        );
        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(
                inboundRecord.eqpId(),
                inboundRecord.traceId()
        )) {
            BusinessObservationLogger.logReceivedMessage(
                    "MES",
                    inboundRecord.messageName(),
                    inboundRecord.topic(),
                    inboundRecord.partition(),
                    inboundRecord.offset(),
                    inboundRecord.eqpId(),
                    inboundRecord.traceId(),
                    inboundRecord.payload()
            );

            final boolean accepted = ingressPort.submit(inboundRecord);
            if (!accepted) {
                throw new IllegalStateException(
                        "Runtime queue overflow while ingesting MES event. topic="
                                + record.topic()
                                + ", partition="
                                + record.partition()
                                + ", offset="
                                + record.offset()
                );
            }

            BusinessObservationLogger.logKafkaInboundAccepted(
                    inboundRecord.topic(),
                    inboundRecord.partition(),
                    inboundRecord.offset(),
                    inboundRecord.eqpId(),
                    inboundRecord.traceId(),
                    inboundRecord.messageName(),
                    "MES"
            );
        }
    }

    /**
     * Creates a copy of an inbound record with a replaced traceId.
     *
     * @param source source record mapped from Kafka payload
     * @param traceId newly generated business runtime traceId
     * @return copied record with the new traceId
     */
    private static BusinessInboundRecord withTraceId(
            final BusinessInboundRecord source,
            final String traceId
    ) {
        return new BusinessInboundRecord(
                source.topic(),
                source.partition(),
                source.offset(),
                source.eqpId(),
                traceId,
                source.messageType(),
                source.messageName(),
                source.payloadRef(),
                source.payload()
        );
    }
}
