package com.nori.tc.ui.adapters.kafka.publish;

import com.nori.tc.messaging.kafka.contract.KafkaHeaderSupport;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import com.nori.tc.ui.adapters.kafka.config.UiKafkaTopicProperties;
import com.nori.tc.ui.core.port.messaging.UiBusinessEventPublishPort;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code tc.ui.events.business} 토픽으로 UI 이벤트를 발행하는 어댑터입니다.
 *
 * <p>역할:</p>
 * <p>eqp_create / eqp_update / eqp_delete 이벤트를 Business Core로 전달합니다.
 * Business Core는 이 토픽을 구독하여 자체 EQP Bean 동기화(추가/수정/삭제)를 수행합니다.</p>
 *
 * <p>Gateway 발행기({@link UiGatewayEventKafkaPublisher})와의 차이:</p>
 * <ul>
 *   <li>eqp_start / eqp_end 는 Gateway 전담이므로 이 토픽으로 발행하지 않습니다.</li>
 *   <li>Business 구독자는 Consumer Group 모드로 동작하므로 route_partition 지정이 불필요합니다.
 *       key(eqpId) 해시 기반으로 파티션이 자동 결정됩니다.</li>
 * </ul>
 *
 * <p>구현 포트: {@link UiBusinessEventPublishPort}</p>
 */
@Component
public class UiBusinessEventKafkaPublisher implements UiBusinessEventPublishPort {

    private static final Logger log = LoggerFactory.getLogger(UiBusinessEventKafkaPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UiKafkaTopicProperties topicProperties;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param kafkaTemplate   Kafka 발행 템플릿
     * @param topicProperties 토픽 이름 설정
     */
    public UiBusinessEventKafkaPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final UiKafkaTopicProperties topicProperties
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
    }

    /**
     * Business 이벤트 토픽으로 Kafka 메시지를 발행합니다.
     *
     * <p>Business 구독자는 Consumer Group 모드이므로 route_partition을 명시하지 않습니다.
     * key(eqpId)를 지정하여 동일 eqpId 메시지는 동일 파티션으로 전달되도록 합니다.
     * 발행은 비동기로 수행되며 콜백에서 성공/실패를 로깅합니다.</p>
     *
     * @param message 발행할 UI Task 메시지 (metadata.traceId, data.eqpId 포함 필수)
     * @throws IllegalArgumentException message가 null인 경우
     */
    @Override
    public void publish(final KafkaUiTaskMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String eqpId     = message.data().eqpId();
        final String traceId   = message.metadata().traceId();
        final String eventType = message.metadata().eventType();
        final String source    = message.metadata().source();
        final String topic     = topicProperties.getBusinessEventsTopic();

        // NOTE: Business 구독자 GROUP 모드 → key(eqpId) hash 라우팅 사용
        // partition을 명시하지 않으므로 ProducerRecord의 partition 파라미터는 null
        final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, eqpId, message);

        // 분산 추적 헤더 추가 (x-trace-id, x-event-type, x-source)
        KafkaHeaderSupport.addTracingHeaders(record, traceId, eventType, source);

        log.info(
                "tc.ui.events.business 발행 시도. topic={}, eqpId={}, traceId={}, eventType={}",
                topic, eqpId, traceId, eventType
        );

        final long sendStartNano = System.nanoTime();

        kafkaTemplate.send(record).whenComplete((sendResult, ex) -> {
            final long latencyMs = elapsedMillis(sendStartNano);

            if (ex != null) {
                // 브로커 전송 실패 — 비동기 콜백이므로 예외를 throw할 수 없음
                log.error(
                        "tc.ui.events.business 발행 실패(비동기). "
                                + "topic={}, eqpId={}, traceId={}, eventType={}, latencyMs={}",
                        topic, eqpId, traceId, eventType, latencyMs,
                        ex
                );
                return;
            }

            // 브로커 승인 완료
            if (log.isDebugEnabled()) {
                log.debug(
                        "tc.ui.events.business 발행 완료. "
                                + "topic={}, partition={}, offset={}, eqpId={}, traceId={}, eventType={}, latencyMs={}",
                        topic,
                        sendResult.getRecordMetadata().partition(),
                        sendResult.getRecordMetadata().offset(),
                        eqpId, traceId, eventType, latencyMs
                );
            }
        });
    }

    /**
     * nanoTime 기준 경과 시간을 밀리초로 환산합니다.
     *
     * @param startNano 시작 nanoTime
     * @return 경과 시간(ms), 0 이상 보장
     */
    private static long elapsedMillis(final long startNano) {
        return Math.max(0L, (System.nanoTime() - startNano) / 1_000_000L);
    }
}
