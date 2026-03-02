package com.nori.tc.ui.adapters.kafka.subscribe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyMessage;
import com.nori.tc.ui.core.port.messaging.UiCommandIngressPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code tc.ui.commands} 토픽을 구독하는 UI Command 수신 어댑터입니다.
 *
 * <p>역할:</p>
 * <p>Gateway와 Business Core가 처리 결과를 {@code tc.ui.commands}에 발행하면,
 * 이 컴포넌트가 수신하여 {@link UiCommandIngressPort}로 전달합니다.
 * {@code UiCommandIngressService}가 포트를 구현하고 eventType에 따라 분기합니다.</p>
 *
 * <p>처리 분기(UiCommandIngressService가 담당):</p>
 * <ul>
 *   <li>EQP_CREATE_REP / EQP_UPDATE_REP / EQP_DELETE_REP →
 *       DualResponseRegistry (traceId + source 기반 양방향 수집)</li>
 *   <li>EQP_START_REP / EQP_END_REP →
 *       AsyncResultStorePort (Redis 임시 저장, front polling)</li>
 * </ul>
 *
 * <p>Consumer 설정:</p>
 * <ul>
 *   <li>Consumer Group: {@code tc-ui-backend-group}</li>
 *   <li>ACK 모드: MANUAL_IMMEDIATE (처리 완료 후 명시적 커밋)</li>
 *   <li>Value 역직렬화: StringDeserializer → ObjectMapper(JSON) → {@link KafkaUiTaskReplyMessage}</li>
 * </ul>
 *
 * <p>ACK 정책:</p>
 * <ul>
 *   <li>JSON 파싱 실패 → WARN 로그 후 acknowledge (복구 불가 메시지는 skip)</li>
 *   <li>ingress 처리 성공 → acknowledge</li>
 *   <li>ingress 처리 예외 → ERROR 로그 후 acknowledge (무한 재시도 방지)</li>
 * </ul>
 */
@Component
public class UiCommandKafkaSubscriber {

    private static final Logger log = LoggerFactory.getLogger(UiCommandKafkaSubscriber.class);

    private final UiCommandIngressPort ingressPort;
    private final ObjectMapper objectMapper;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param ingressPort  tc.ui.commands 수신 처리 포트 (UiCommandIngressService 구현체)
     * @param objectMapper JSON 역직렬화용 ObjectMapper
     */
    public UiCommandKafkaSubscriber(
            final UiCommandIngressPort ingressPort,
            final ObjectMapper objectMapper
    ) {
        this.ingressPort = Objects.requireNonNull(ingressPort, "ingressPort is null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
    }

    /**
     * {@code tc.ui.commands} 토픽 메시지를 수신하여 처리합니다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>JSON 문자열을 {@link KafkaUiTaskReplyMessage}로 역직렬화</li>
     *   <li>{@link UiCommandIngressPort#handle(KafkaUiTaskReplyMessage)} 호출 (eventType 기반 분기)</li>
     *   <li>{@link Acknowledgment#acknowledge()} 호출로 offset 커밋</li>
     * </ol>
     *
     * @param record Kafka consumer record (key=eqpId, value=JSON 문자열)
     * @param ack    MANUAL_IMMEDIATE 모드에서 offset 커밋을 제어하는 Acknowledgment 객체
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
        final String topic     = record.topic();
        final int    partition = record.partition();
        final long   offset    = record.offset();
        final String key       = record.key();

        // ─────────────────────────────────────────────────────────
        // Step 1: JSON → KafkaUiTaskReplyMessage 역직렬화
        // 파싱 실패는 메시지 자체의 문제이므로 acknowledge 후 skip
        // ─────────────────────────────────────────────────────────
        final KafkaUiTaskReplyMessage reply;
        try {
            reply = objectMapper.readValue(record.value(), KafkaUiTaskReplyMessage.class);
        } catch (Exception ex) {
            log.warn(
                    "tc.ui.commands 메시지 파싱 실패 - skip. "
                            + "topic={}, partition={}, offset={}, key={}, error={}",
                    topic, partition, offset, key, ex.getMessage()
            );
            // 복구 불가 메시지 → 커밋하여 무한 재처리 방지
            ack.acknowledge();
            return;
        }

        final String eventType = safeText(reply.metadata() != null ? reply.metadata().eventType() : null);
        final String traceId   = safeText(reply.metadata() != null ? reply.metadata().traceId()   : null);
        final String source    = safeText(reply.metadata() != null ? reply.metadata().source()     : null);

        log.info(
                "tc.ui.commands 수신. topic={}, partition={}, offset={}, eqpId={}, traceId={}, eventType={}, source={}",
                topic, partition, offset, key, traceId, eventType, source
        );

        // ─────────────────────────────────────────────────────────
        // Step 2: eventType 기반 분기 처리 (UiCommandIngressService 위임)
        // EQP_CREATE_REP / EQP_UPDATE_REP / EQP_DELETE_REP → DualResponseRegistry
        // EQP_START_REP  / EQP_END_REP                    → AsyncResultStorePort (Redis)
        // ─────────────────────────────────────────────────────────
        try {
            ingressPort.handle(reply);
        } catch (Exception ex) {
            // DualResponseRegistry traceId 불일치, Redis 장애 등
            // 재처리해도 동일한 오류가 반복될 수 있으므로 acknowledge 후 ERROR 로그만 남김
            log.error(
                    "tc.ui.commands 처리 실패 - acknowledge 후 skip. "
                            + "topic={}, partition={}, offset={}, traceId={}, eventType={}, source={}",
                    topic, partition, offset, traceId, eventType, source,
                    ex
            );
            ack.acknowledge();
            return;
        }

        // Step 3: 정상 처리 완료 → offset 커밋
        ack.acknowledge();

        if (log.isDebugEnabled()) {
            log.debug(
                    "tc.ui.commands 처리 완료 - offset 커밋. "
                            + "topic={}, partition={}, offset={}, traceId={}, eventType={}",
                    topic, partition, offset, traceId, eventType
            );
        }
    }

    /**
     * null 또는 공백 문자열을 "N/A"로 변환합니다.
     *
     * <p>로그 출력 시 null 리터럴 대신 명시적인 값을 표시하기 위해 사용합니다.</p>
     *
     * @param value 원본 문자열
     * @return 정규화된 로그 표시 문자열
     */
    private static String safeText(final String value) {
        return (value == null || value.isBlank()) ? "N/A" : value.trim();
    }
}
