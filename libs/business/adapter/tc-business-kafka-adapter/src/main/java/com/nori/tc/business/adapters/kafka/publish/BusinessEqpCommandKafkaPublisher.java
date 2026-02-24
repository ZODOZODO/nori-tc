package com.nori.tc.business.adapters.kafka.publish;

import com.nori.tc.business.adapters.kafka.contract.BusinessKafkaContractSupport;
import com.nori.tc.business.adapters.kafka.publish.contract.BusinessEqpCommandKafkaMessage;
import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.messaging.BusinessEqpCommandMessage;
import com.nori.tc.business.core.messaging.BusinessEqpCommandPublishPort;
import com.nori.tc.messaging.domain.kafka.contract.TcCommonKafkaMetadata;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaEnvelope;
import com.nori.tc.messaging.kafka.contract.KafkaHeaderSupport;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Business -> Gateway EQP 명령 Kafka 발행 어댑터입니다.
 *
 * <p>발행 토픽:
 * {@code tc.eqp.commands}</p>
 */
@Component
public class BusinessEqpCommandKafkaPublisher implements BusinessEqpCommandPublishPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessEqpCommandKafkaPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BusinessCoreRuntimeProperties runtimeProperties;
    private final BusinessKafkaContractSupport contractSupport;

    /**
     * 발행 어댑터 의존성을 주입받습니다.
     *
     * @param kafkaTemplate KafkaTemplate
     * @param runtimeProperties Business 런타임 프로퍼티
     * @param contractSupport Kafka 계약 검증/키 정책 지원기
     */
    public BusinessEqpCommandKafkaPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final BusinessCoreRuntimeProperties runtimeProperties,
            final BusinessKafkaContractSupport contractSupport
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");
        this.contractSupport = Objects.requireNonNull(contractSupport, "contractSupport is null");
    }

    /**
     * EQP 명령을 Kafka로 발행합니다.
     *
     * <p>처리 순서:
     * 1) 공통 metadata/envelope 생성
     * 2) metadata 검증
     * 3) key 정책 적용
     * 4) 레거시 호환 wire contract로 publish</p>
     *
     * @param command 발행할 EQP 명령
     * @throws Exception 발행 실패
     */
    @Override
    public void publish(final BusinessEqpCommandMessage command) throws Exception {
        Objects.requireNonNull(command, "command is null");

        final String topic = runtimeProperties.getKafka().getEqpCommandsTopic();
        final String now = Instant.now().toString();

        final TcCommonKafkaMetadata metadata = contractSupport.createCommonMetadata(
                command.eventType(),
                now,
                runtimeProperties.getKafka().getSource(),
                command.traceId(),
                null
        );

        final Map<String, Object> envelopeData = new LinkedHashMap<>();
        envelopeData.put("eqpId", command.eqpId());
        envelopeData.put("interfaceType", command.interfaceType());
        envelopeData.put("rawMessage", command.rawMessage());
        if (command.transactionId() != null) {
            envelopeData.put("transactionId", command.transactionId());
        }
        final TcKafkaEnvelope<TcCommonKafkaMetadata, Map<String, Object>> envelope =
                new TcKafkaEnvelope<>(metadata, envelopeData);

        // 5) 공통 envelope/metadata 검증 연결
        contractSupport.validateEnvelope(topic, envelope);

        // 6) key 정책 적용 (MES 외 토픽은 eqpId)
        final String key = contractSupport.resolveKafkaKey(topic, metadata, command.eqpId());

        final BusinessEqpCommandKafkaMessage payload = new BusinessEqpCommandKafkaMessage(
                new BusinessEqpCommandKafkaMessage.BusinessEqpCommandKafkaMetadata(
                        metadata.eventType(),
                        metadata.timestamp(),
                        metadata.source(),
                        metadata.traceId()
                ),
                new BusinessEqpCommandKafkaMessage.BusinessEqpCommandKafkaData(
                        command.transactionId(),
                        command.eqpId(),
                        command.interfaceType(),
                        null,
                        command.rawMessage()
                )
        );

        final ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, key, payload);
        KafkaHeaderSupport.addTracingHeaders(
                producerRecord,
                metadata.traceId(),
                metadata.eventType(),
                metadata.source()
        );

        log.debug("Publishing EQP command. topic={}, key={}, eqpId={}, eventType={}, traceId={}, interfaceType={}",
                topic,
                key,
                command.eqpId(),
                metadata.eventType(),
                metadata.traceId(),
                command.interfaceType());

        try {
            kafkaTemplate.send(producerRecord).get();
        } catch (Exception ex) {
            log.error("EQP command publish failed. topic={}, key={}, eqpId={}, eventType={}, traceId={}",
                    topic,
                    key,
                    command.eqpId(),
                    metadata.eventType(),
                    metadata.traceId(),
                    ex);
            throw ex;
        }

        log.debug("EQP command published. topic={}, key={}, eqpId={}, eventType={}, traceId={}",
                topic,
                key,
                command.eqpId(),
                metadata.eventType(),
                metadata.traceId());
    }
}
