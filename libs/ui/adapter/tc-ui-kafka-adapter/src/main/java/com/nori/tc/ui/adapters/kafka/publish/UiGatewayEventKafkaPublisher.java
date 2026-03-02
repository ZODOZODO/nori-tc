package com.nori.tc.ui.adapters.kafka.publish;

import com.nori.tc.messaging.kafka.contract.KafkaHeaderSupport;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import com.nori.tc.ui.adapters.kafka.config.UiKafkaTopicProperties;
import com.nori.tc.ui.core.port.messaging.UiGatewayEqpRoutePartitionLookupPort;
import com.nori.tc.ui.core.port.messaging.UiGatewayEventPublishPort;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code tc.ui.events.gateway} 토픽으로 UI 이벤트를 발행하는 어댑터입니다.
 *
 * <p>역할:</p>
 * <p>eqp_create / eqp_update / eqp_delete / eqp_start / eqp_end 이벤트를
 * Gateway로 전달합니다. eqp_start / eqp_end는 Gateway가 단독으로 처리하므로
 * Business 토픽과 달리 이 토픽에만 발행합니다.</p>
 *
 * <p>[U13] route_partition 명시 발행 규칙:</p>
 * <ol>
 *   <li>{@link UiGatewayEqpRoutePartitionLookupPort}로 eqpId에 배정된 route_partition 조회</li>
 *   <li>빈 Optional 또는 음수이면 발행을 차단하고 ERROR 로그를 남깁니다</li>
 *   <li>{@code ProducerRecord(topic, routePartition, key=eqpId, payload)} 생성</li>
 *   <li>추적 헤더(x-trace-id, x-event-type, x-source)를 레코드에 추가</li>
 *   <li>{@code KafkaTemplate.send(record)} 비동기 발행 후 콜백에서 성공/실패를 로깅</li>
 * </ol>
 *
 * <p>NOTE: {@code KafkaTemplate.send(topic, key, value)} 직접 호출은 금지합니다.
 * partition을 명시하지 않으면 key 해시 기반으로 파티션이 선택되어
 * 해당 설비를 관리하는 Gateway 인스턴스에 메시지가 전달되지 않을 수 있습니다.</p>
 *
 * <p>구현 포트: {@link UiGatewayEventPublishPort}</p>
 */
@Component
public class UiGatewayEventKafkaPublisher implements UiGatewayEventPublishPort {

    private static final Logger log = LoggerFactory.getLogger(UiGatewayEventKafkaPublisher.class);

    /**
     * 발행 차단 사유 상수 (ERROR 로그 reason 필드용).
     */
    private static final String REASON_NO_ROUTE_PARTITION = "ROUTE_PARTITION_NOT_ASSIGNED";
    private static final String REASON_NEGATIVE_PARTITION  = "ROUTE_PARTITION_NEGATIVE";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UiKafkaTopicProperties topicProperties;
    private final UiGatewayEqpRoutePartitionLookupPort routePartitionLookupPort;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param kafkaTemplate              Kafka 발행 템플릿
     * @param topicProperties            토픽 이름 설정
     * @param routePartitionLookupPort   eqpId → route_partition 조회 포트 (U13)
     */
    public UiGatewayEventKafkaPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final UiKafkaTopicProperties topicProperties,
            final UiGatewayEqpRoutePartitionLookupPort routePartitionLookupPort
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.routePartitionLookupPort = Objects.requireNonNull(routePartitionLookupPort, "routePartitionLookupPort is null");
    }

    /**
     * Gateway 이벤트 토픽으로 Kafka 메시지를 발행합니다.
     *
     * <p>eqpId의 route_partition을 조회하여 명시적 파티션으로 발행합니다.
     * 발행은 비동기로 수행되며 콜백에서 성공/실패를 로깅합니다.</p>
     *
     * @param message 발행할 UI Task 메시지 (metadata.traceId, data.eqpId 포함 필수)
     * @throws IllegalArgumentException  message가 null인 경우
     * @throws IllegalStateException     route_partition이 미배정 또는 음수인 경우 (U13 발행 차단)
     */
    @Override
    public void publish(final KafkaUiTaskMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String eqpId     = message.data().eqpId();
        final String traceId   = message.metadata().traceId();
        final String eventType = message.metadata().eventType();
        final String source    = message.metadata().source();
        final String topic     = topicProperties.getGatewayEventsTopic();

        // ─────────────────────────────────────────────────────────
        // [U13] route_partition 조회 및 유효성 검증
        // route_partition이 미배정이거나 음수이면 발행을 차단합니다.
        // 잘못된 파티션으로 발행하면 해당 설비를 관리하는 Gateway가
        // 메시지를 수신하지 못하므로 발행 자체를 거부합니다.
        // ─────────────────────────────────────────────────────────
        final Optional<Integer> routePartitionOpt = routePartitionLookupPort.findRoutePartition(eqpId);

        if (routePartitionOpt.isEmpty()) {
            log.error(
                    "tc.ui.events.gateway 발행 차단: route_partition 미배정. "
                            + "topic={}, eqpId={}, traceId={}, eventType={}, reason={}",
                    topic, eqpId, traceId, eventType, REASON_NO_ROUTE_PARTITION
            );
            throw new IllegalStateException(
                    "Gateway 발행 실패: eqpId=" + eqpId + "에 route_partition이 배정되지 않았습니다."
            );
        }

        final int routePartition = routePartitionOpt.get();
        if (routePartition < 0) {
            log.error(
                    "tc.ui.events.gateway 발행 차단: route_partition 음수. "
                            + "topic={}, eqpId={}, traceId={}, eventType={}, routePartition={}, reason={}",
                    topic, eqpId, traceId, eventType, routePartition, REASON_NEGATIVE_PARTITION
            );
            throw new IllegalStateException(
                    "Gateway 발행 실패: eqpId=" + eqpId + "의 route_partition=" + routePartition + "은 유효하지 않습니다."
            );
        }

        // ─────────────────────────────────────────────────────────
        // ProducerRecord 생성: partition을 명시하여 고정 파티션 발행
        // KafkaTemplate.send(topic, key, value) 직접 호출 금지 (U13)
        // ─────────────────────────────────────────────────────────
        final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, routePartition, eqpId, message);

        // 분산 추적 헤더 추가 (x-trace-id, x-event-type, x-source)
        KafkaHeaderSupport.addTracingHeaders(record, traceId, eventType, source);

        log.info(
                "tc.ui.events.gateway 발행 시도. topic={}, partition={}, eqpId={}, traceId={}, eventType={}",
                topic, routePartition, eqpId, traceId, eventType
        );

        final long sendStartNano = System.nanoTime();

        kafkaTemplate.send(record).whenComplete((sendResult, ex) -> {
            final long latencyMs = elapsedMillis(sendStartNano);

            if (ex != null) {
                // 브로커 전송 실패 — 비동기 콜백이므로 예외를 throw할 수 없음
                // 호출부(UseCase)는 이 실패를 인지하지 못하므로 반드시 ERROR 로그를 남김
                log.error(
                        "tc.ui.events.gateway 발행 실패(비동기). "
                                + "topic={}, partition={}, eqpId={}, traceId={}, eventType={}, latencyMs={}",
                        topic, routePartition, eqpId, traceId, eventType, latencyMs,
                        ex
                );
                return;
            }

            // 브로커 승인 완료
            if (log.isDebugEnabled()) {
                log.debug(
                        "tc.ui.events.gateway 발행 완료. "
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
