package com.nori.tc.business.adapters.kafka.publish;

import com.nori.tc.business.adapters.kafka.contract.BusinessKafkaContractSupport;
import com.nori.tc.business.adapters.kafka.publish.contract.BusinessEqpCommandKafkaMessage;
import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.messaging.BusinessEqpCommandMessage;
import com.nori.tc.business.core.messaging.BusinessEqpCommandPublishPort;
import com.nori.tc.business.core.messaging.BusinessEqpRoutePartitionLookupPort;
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
    private final BusinessEqpRoutePartitionLookupPort routePartitionLookupPort;

    /**
     * 발행 어댑터 의존성을 주입받습니다.
     *
     * @param kafkaTemplate KafkaTemplate
     * @param runtimeProperties Business 런타임 프로퍼티
     * @param contractSupport Kafka 계약 검증/키 정책 지원기
     * @param routePartitionLookupPort tc_eqp.route_partition 조회 포트
     */
    public BusinessEqpCommandKafkaPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final BusinessCoreRuntimeProperties runtimeProperties,
            final BusinessKafkaContractSupport contractSupport,
            final BusinessEqpRoutePartitionLookupPort routePartitionLookupPort
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");
        this.contractSupport = Objects.requireNonNull(contractSupport, "contractSupport is null");
        this.routePartitionLookupPort = Objects.requireNonNull(routePartitionLookupPort, "routePartitionLookupPort is null");

        log.info("BusinessEqpCommandKafkaPublisher initialized. routingPolicy=explicit-partition, routePartitionSource=tc_eqp.route_partition");
    }

    /**
     * EQP 명령을 Kafka로 발행합니다.
     *
     * <p>처리 순서:
     * 1) 공통 metadata/envelope 생성
     * 2) metadata 검증
     * 3) key 정책 적용
     * 4) tc_eqp.route_partition 조회 및 고정 partition 라우팅
     * 5) 레거시 호환 wire contract로 publish</p>
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
        final int routePartition = resolveRequiredRoutePartition(command.eqpId(), topic, metadata.traceId(), metadata.eventType());

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

        /*
         * U12부터 Gateway 대상 EQP command는 Kafka key hash 자동 파티셔닝에 의존하지 않고,
         * tc_eqp.route_partition을 기준으로 명시 partition 발행합니다.
         * key(eqpId)는 순서성/추적/계약 검증 용도로 그대로 유지합니다.
         */
        final ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, routePartition, key, payload);
        KafkaHeaderSupport.addTracingHeaders(
                producerRecord,
                metadata.traceId(),
                metadata.eventType(),
                metadata.source()
        );

        log.debug("EQP 명령 발행 준비 완료. topic={}, partition={}, key={}, eqpId={}, eventType={}, traceId={}, interfaceType={}",
                topic,
                routePartition,
                key,
                command.eqpId(),
                metadata.eventType(),
                metadata.traceId(),
                command.interfaceType());

        try {
            kafkaTemplate.send(producerRecord).get();
        } catch (Exception ex) {
            log.error("EQP 명령 발행 실패. topic={}, partition={}, key={}, eqpId={}, eventType={}, traceId={}",
                    topic,
                    routePartition,
                    key,
                    command.eqpId(),
                    metadata.eventType(),
                    metadata.traceId(),
                    ex);
            throw ex;
        }

        log.debug("EQP 명령 발행 완료. topic={}, partition={}, key={}, eqpId={}, eventType={}, traceId={}",
                topic,
                routePartition,
                key,
                command.eqpId(),
                metadata.eventType(),
                metadata.traceId());
    }

    /**
     * 발행 대상 설비의 고정 라우팅 partition을 조회하고, 필수 조건을 만족하지 않으면 예외를 발생시킵니다.
     *
     * <p>U12 설계 규칙상 {@code tc.eqp.commands}는 {@code tc_eqp.route_partition} 기반 명시 partition 발행을 사용합니다.</p>
     * <p>따라서 아래 조건은 운영 설정 오류로 간주하여 발행을 차단합니다.</p>
     * <p>1) eqpId가 비어 있음</p>
     * <p>2) tc_eqp 미존재</p>
     * <p>3) route_partition 미배정(null)</p>
     * <p>4) route_partition 음수</p>
     *
     * @param eqpId 설비 식별자
     * @param topic 발행 토픽명(로그용)
     * @param traceId traceId(로그용)
     * @param eventType 이벤트 타입(로그용)
     * @return 발행에 사용할 고정 partition 번호
     */
    private int resolveRequiredRoutePartition(
            final String eqpId,
            final String topic,
            final String traceId,
            final String eventType
    ) {
        if (eqpId == null || eqpId.isBlank()) {
            final IllegalStateException ex = new IllegalStateException("eqpId is required for route_partition lookup");
            log.error("EQP 명령 발행 차단. 사유=eqpId 비어 있음, topic={}, eventType={}, traceId={}",
                    topic,
                    eventType,
                    traceId,
                    ex);
            throw ex;
        }

        if (log.isDebugEnabled()) {
            log.debug("EQP 명령 route_partition 조회 시작. topic={}, eqpId={}, eventType={}, traceId={}",
                    topic,
                    eqpId,
                    eventType,
                    traceId);
        }

        final Integer routePartition = routePartitionLookupPort.findRoutePartitionByEqpId(eqpId).orElse(null);
        if (routePartition == null) {
            final IllegalStateException ex = new IllegalStateException(
                    "route_partition not found for eqpId=" + eqpId
            );
            log.error("EQP 명령 발행 차단. 사유=route_partition 미배정/미존재, topic={}, eqpId={}, eventType={}, traceId={}",
                    topic,
                    eqpId,
                    eventType,
                    traceId,
                    ex);
            throw ex;
        }
        if (routePartition < 0) {
            final IllegalStateException ex = new IllegalStateException(
                    "route_partition must be >= 0 for eqpId=" + eqpId + " (actual=" + routePartition + ")"
            );
            log.error("EQP 명령 발행 차단. 사유=route_partition 음수, topic={}, eqpId={}, routePartition={}, eventType={}, traceId={}",
                    topic,
                    eqpId,
                    routePartition,
                    eventType,
                    traceId,
                    ex);
            throw ex;
        }

        if (log.isDebugEnabled()) {
            log.debug("EQP 명령 route_partition 조회 완료. topic={}, eqpId={}, routePartition={}, eventType={}, traceId={}",
                    topic,
                    eqpId,
                    routePartition,
                    eventType,
                    traceId);
        }
        return routePartition;
    }
}
