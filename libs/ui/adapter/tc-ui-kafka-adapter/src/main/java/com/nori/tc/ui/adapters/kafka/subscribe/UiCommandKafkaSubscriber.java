package com.nori.tc.ui.adapters.kafka.subscribe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyMessage;
import com.nori.tc.ui.adapters.kafka.config.UiKafkaPublishProperties;
import com.nori.tc.ui.adapters.kafka.config.UiKafkaTopicProperties;
import com.nori.tc.ui.core.model.UiCommandReply;
import com.nori.tc.ui.core.port.messaging.UiCommandIngressPort;
import com.nori.tc.ui.domain.task.UiTaskStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * {@code tc.ui.commands} 토픽을 구독하는 UI Command 수신 어댑터입니다.
 *
 * <p>핵심 정책:</p>
 * <ul>
 *   <li>파싱 실패(JSON 불량): 즉시 DLT 전송 후 ACK (무한 재처리 방지)</li>
 *   <li>비즈니스 처리 실패: 오류 로그 후 ACK</li>
 *   <li>인프라성 실패(Kafka 컨테이너/브로커): 예외 재전파 (컨테이너 에러 핸들러 위임)</li>
 * </ul>
 */
@Component
public class UiCommandKafkaSubscriber {

    private static final Logger log = LoggerFactory.getLogger(UiCommandKafkaSubscriber.class);
    private static final String DLT_ERROR_HEADER = "x-dlt-error";
    private static final String DLT_CLASS_HEADER = "x-dlt-error-class";

    private final UiCommandIngressPort ingressPort;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UiKafkaTopicProperties topicProperties;
    private final UiKafkaPublishProperties publishProperties;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param ingressPort tc.ui.commands 수신 처리 포트
     * @param objectMapper JSON 역직렬화용 ObjectMapper
     * @param kafkaTemplate DLT 전송용 KafkaTemplate
     * @param topicProperties Kafka 토픽 설정
     * @param publishProperties 발행 타임아웃 설정
     */
    public UiCommandKafkaSubscriber(
            final UiCommandIngressPort ingressPort,
            final ObjectMapper objectMapper,
            final KafkaTemplate<String, Object> kafkaTemplate,
            final UiKafkaTopicProperties topicProperties,
            final UiKafkaPublishProperties publishProperties
    ) {
        this.ingressPort = Objects.requireNonNull(ingressPort, "ingressPort is null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.publishProperties = Objects.requireNonNull(publishProperties, "publishProperties is null");
    }

    /**
     * {@code tc.ui.commands} 토픽 메시지를 수신하여 처리합니다.
     *
     * @param record Kafka consumer record (key=eqpId, value=JSON 문자열)
     * @param ack MANUAL_IMMEDIATE ACK 핸들러
     */
    @KafkaListener(
            topics = "${tc.ui.backend.kafka.commands-topic}",
            groupId = "tc-ui-backend-group",
            containerFactory = "uiCommandListenerContainerFactory"
    )
    public void onMessage(
            final ConsumerRecord<String, String> record,
            final Acknowledgment ack
    ) {
        final String topic = record.topic();
        final int partition = record.partition();
        final long offset = record.offset();
        final String key = record.key();

        final UiCommandReply commandReply;
        try {
            final KafkaUiTaskReplyMessage rawReply =
                    objectMapper.readValue(record.value(), KafkaUiTaskReplyMessage.class);
            commandReply = toCommandReply(rawReply);
        } catch (JsonProcessingException ex) {
            log.warn(
                    "tc.ui.commands 메시지 JSON 파싱 실패 - DLT 전송 후 skip. topic={}, partition={}, offset={}, key={}, error={}",
                    topic, partition, offset, key, ex.getOriginalMessage()
            );
            publishParseFailureToDlt(record, ex);
            ack.acknowledge();
            return;
        } catch (RuntimeException ex) {
            log.error(
                    "tc.ui.commands 파싱 단계 인프라 오류 - 재시도 위임. topic={}, partition={}, offset={}, key={}",
                    topic, partition, offset, key, ex
            );
            throw ex;
        }

        log.info(
                "tc.ui.commands 수신. topic={}, partition={}, offset={}, eqpId={}, traceId={}, eventType={}, source={}",
                topic, partition, offset, key, commandReply.traceId(), commandReply.eventType(), commandReply.source()
        );

        try {
            ingressPort.handle(commandReply);
        } catch (Exception ex) {
            if (isInfrastructureException(ex)) {
                log.error(
                        "tc.ui.commands 처리 중 인프라 오류 - 재시도 위임. topic={}, partition={}, offset={}, traceId={}, eventType={}",
                        topic, partition, offset, commandReply.traceId(), commandReply.eventType(), ex
                );
                if (ex instanceof RuntimeException runtimeEx) {
                    throw runtimeEx;
                }
                throw new IllegalStateException("tc.ui.commands 인프라 처리 오류", ex);
            }

            log.error(
                    "tc.ui.commands 비즈니스 처리 실패 - ACK 후 skip. topic={}, partition={}, offset={}, traceId={}, eventType={}",
                    topic, partition, offset, commandReply.traceId(), commandReply.eventType(), ex
            );
            ack.acknowledge();
            return;
        }

        ack.acknowledge();
        if (log.isDebugEnabled()) {
            log.debug(
                    "tc.ui.commands 처리 완료 - offset 커밋. topic={}, partition={}, offset={}, traceId={}, eventType={}",
                    topic, partition, offset, commandReply.traceId(), commandReply.eventType()
            );
        }
    }

    /**
     * Kafka 계약 응답을 core 기술 중립 DTO로 변환합니다.
     *
     * @param reply Kafka 계약 응답
     * @return 변환된 UiCommandReply
     */
    private UiCommandReply toCommandReply(final KafkaUiTaskReplyMessage reply) {
        final UiTaskStatus status = parseStatusOrFail(
                reply.metadata().traceId(),
                reply.metadata().eventType(),
                reply.data().STATUS()
        );
        return new UiCommandReply(
                reply.metadata().traceId(),
                reply.metadata().source(),
                reply.metadata().eventType(),
                reply.data().eqpId(),
                reply.data().interfaceType(),
                status,
                reply.data().ERRORCODE(),
                reply.data().ERRORMSG()
        );
    }

    /**
     * 파싱 실패 레코드를 DLT 토픽으로 전송합니다.
     *
     * @param record 원본 수신 레코드
     * @param ex 파싱 예외
     */
    private void publishParseFailureToDlt(
            final ConsumerRecord<String, String> record,
            final JsonProcessingException ex
    ) {
        final ProducerRecord<String, Object> dltRecord = new ProducerRecord<>(
                topicProperties.getCommandsDltTopic(),
                record.partition(),
                record.key(),
                record.value()
        );
        copyHeaders(record, dltRecord);
        dltRecord.headers().add(DLT_ERROR_HEADER, safeText(ex.getOriginalMessage()).getBytes(StandardCharsets.UTF_8));
        dltRecord.headers().add(DLT_CLASS_HEADER, ex.getClass().getName().getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(dltRecord)
                    .get(publishProperties.getPublishTimeoutSeconds(), TimeUnit.SECONDS);
            log.warn(
                    "tc.ui.commands 파싱 실패 메시지 DLT 전송 완료. dltTopic={}, partition={}, offset={}, key={}",
                    topicProperties.getCommandsDltTopic(),
                    record.partition(),
                    record.offset(),
                    record.key()
            );
        } catch (Exception dltEx) {
            log.error(
                    "tc.ui.commands DLT 전송 실패 - 원본 payload 기록. dltTopic={}, partition={}, offset={}, key={}, payload={}",
                    topicProperties.getCommandsDltTopic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    record.value(),
                    dltEx
            );
        }
    }

    /**
     * 원본 레코드 헤더를 DLT 레코드로 복사합니다.
     *
     * @param source 원본 ConsumerRecord
     * @param target 대상 ProducerRecord
     */
    private static void copyHeaders(
            final ConsumerRecord<String, String> source,
            final ProducerRecord<String, Object> target
    ) {
        for (Header header : source.headers()) {
            target.headers().add(header);
        }
    }

    /**
     * 문자열 STATUS 값을 UiTaskStatus로 변환합니다.
     *
     * @param traceId 로그 추적용 traceId
     * @param eventType 로그 추적용 eventType
     * @param rawStatus 원본 STATUS 문자열
     * @return 변환된 UiTaskStatus (미지원 값은 FAIL로 보정)
     */
    private UiTaskStatus parseStatusOrFail(
            final String traceId,
            final String eventType,
            final String rawStatus
    ) {
        try {
            return UiTaskStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (Exception ex) {
            log.warn(
                    "알 수 없는 STATUS 값 - FAIL로 보정. traceId={}, eventType={}, status={}",
                    safeText(traceId), safeText(eventType), safeText(rawStatus)
            );
            return UiTaskStatus.FAIL;
        }
    }

    /**
     * 인프라성 예외 여부를 판별합니다.
     *
     * @param ex 판별 대상 예외
     * @return 인프라성 예외면 true
     */
    private static boolean isInfrastructureException(final Throwable ex) {
        if (ex instanceof KafkaException || ex.getCause() instanceof KafkaException) {
            return true;
        }
        // Spring Kafka 버전에 따라 ContainerStoppedException 타입이 노출되지 않을 수 있으므로
        // 클래스명 문자열로 방어적으로 판별합니다.
        final String className = ex.getClass().getSimpleName();
        final String causeClassName = ex.getCause() == null ? "" : ex.getCause().getClass().getSimpleName();
        return "ContainerStoppedException".equals(className)
                || "ContainerStoppedException".equals(causeClassName);
    }

    /**
     * null/blank 값을 로그용 문자열로 정규화합니다.
     *
     * @param value 원본 문자열
     * @return null-safe 문자열
     */
    private static String safeText(final String value) {
        return (value == null || value.isBlank()) ? "N/A" : value.trim();
    }
}
