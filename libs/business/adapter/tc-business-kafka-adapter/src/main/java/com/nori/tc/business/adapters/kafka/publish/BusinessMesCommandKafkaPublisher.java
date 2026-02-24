package com.nori.tc.business.adapters.kafka.publish;

import com.nori.tc.business.adapters.kafka.contract.BusinessKafkaContractSupport;
import com.nori.tc.business.adapters.kafka.publish.contract.BusinessMesCommandKafkaMessage;
import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.messaging.BusinessMesCommandMessage;
import com.nori.tc.business.core.messaging.BusinessMesCommandPublishPort;
import com.nori.tc.messaging.domain.kafka.contract.TcKafkaEnvelope;
import com.nori.tc.messaging.domain.kafka.contract.TcMesKafkaMetadata;
import com.nori.tc.messaging.kafka.starter.contract.KafkaHeaderSupport;
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
 * Business -> 외부 MES 어댑터 명령 Kafka 발행 어댑터입니다.
 *
 * <p>발행 토픽:
 * {@code tc.mes.commands}</p>
 */
@Component
public class BusinessMesCommandKafkaPublisher implements BusinessMesCommandPublishPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessMesCommandKafkaPublisher.class);

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
    public BusinessMesCommandKafkaPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final BusinessCoreRuntimeProperties runtimeProperties,
            final BusinessKafkaContractSupport contractSupport
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");
        this.contractSupport = Objects.requireNonNull(contractSupport, "contractSupport is null");
    }

    /**
     * MES 명령을 Kafka로 발행합니다.
     *
     * <p>처리 순서:
     * 1) 공통 metadata/envelope 생성
     * 2) metadata 검증
     * 3) key 정책 적용
     * 4) 레거시 호환 wire contract로 publish</p>
     *
     * @param command 발행할 MES 명령
     * @throws Exception 발행 실패
     */
    @Override
    public void publish(final BusinessMesCommandMessage command) throws Exception {
        Objects.requireNonNull(command, "command is null");

        final String topic = runtimeProperties.getKafka().getMesCommandsTopic();
        final String now = Instant.now().toString();

        final TcMesKafkaMetadata metadata = contractSupport.createMesMetadata(
                command.eventType(),
                now,
                runtimeProperties.getKafka().getSource(),
                command.correlationId(),
                null
        );

        final Map<String, Object> envelopeData = new LinkedHashMap<>(command.data());
        envelopeData.putIfAbsent("eqpId", command.eqpId());
        if (command.traceId() != null) {
            envelopeData.putIfAbsent("traceId", command.traceId());
        }

        final TcKafkaEnvelope<TcMesKafkaMetadata, Map<String, Object>> envelope =
                new TcKafkaEnvelope<>(metadata, envelopeData);

        // 5) 공통 envelope/metadata 검증 연결
        contractSupport.validateEnvelope(topic, envelope);

        // 6) key 정책 적용 (MES 토픽은 correlationId)
        final String key = contractSupport.resolveKafkaKey(topic, metadata, command.eqpId());

        final BusinessMesCommandKafkaMessage payload = new BusinessMesCommandKafkaMessage(
                new BusinessMesCommandKafkaMessage.BusinessMesCommandKafkaMetadata(
                        metadata.eventType(),
                        metadata.timestamp(),
                        metadata.source(),
                        metadata.correlationId()
                ),
                envelopeData
        );

        final ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, key, payload);
        KafkaHeaderSupport.addTracingHeaders(
                producerRecord,
                command.traceId() == null ? metadata.correlationId() : command.traceId(),
                metadata.eventType(),
                metadata.source()
        );

        log.debug("Publishing MES command. topic={}, key={}, correlationId={}, eqpId={}, eventType={}",
                topic,
                key,
                metadata.correlationId(),
                command.eqpId(),
                metadata.eventType());

        try {
            kafkaTemplate.send(producerRecord).get();
        } catch (Exception ex) {
            log.error("MES command publish failed. topic={}, key={}, correlationId={}, eqpId={}, eventType={}",
                    topic,
                    key,
                    metadata.correlationId(),
                    command.eqpId(),
                    metadata.eventType(),
                    ex);
            throw ex;
        }

        if (log.isDebugEnabled()) {
            log.debug("MES command published. topic={}, key={}, correlationId={}, eqpId={}, eventType={}",
                    topic,
                    key,
                    metadata.correlationId(),
                    command.eqpId(),
                    metadata.eventType());
        }
    }
}
