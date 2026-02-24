package com.nori.tc.messaging.kafka.contract;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka {@link ProducerRecord} 헤더 처리 공통 유틸리티입니다.
 *
 * <p>여러 발행 어댑터에서 반복되는 문자열 헤더 추가 로직을 공통화하고,
 * UTF-8 인코딩 규칙을 일관되게 적용하기 위해 사용합니다.</p>
 */
public final class KafkaHeaderSupport {

    /**
     * 분산 추적 식별자 헤더 키입니다.
     */
    public static final String TRACE_ID = "x-trace-id";

    /**
     * 이벤트 타입 헤더 키입니다.
     */
    public static final String EVENT_TYPE = "x-event-type";

    /**
     * 발행 주체(source) 헤더 키입니다.
     */
    public static final String SOURCE = "x-source";

    /**
     * 유틸리티 클래스 생성 방지 생성자입니다.
     */
    private KafkaHeaderSupport() {
        // 인스턴스 생성 방지
    }

    /**
     * 문자열 헤더 맵을 Kafka 레코드 헤더로 복사합니다.
     *
     * @param record  대상 Kafka ProducerRecord
     * @param headers 복사할 문자열 헤더 맵
     */
    public static void copyStringHeaders(final ProducerRecord<String, ?> record, final Map<String, String> headers) {
        Objects.requireNonNull(record, "record is null");
        if (headers == null || headers.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            putIfHasText(record, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 공통 추적 헤더(traceId/eventType/source)를 한 번에 추가합니다.
     *
     * @param record    대상 Kafka ProducerRecord
     * @param traceId   추적 식별자
     * @param eventType 이벤트 타입
     * @param source    발행 주체
     */
    public static void addTracingHeaders(
            final ProducerRecord<String, ?> record,
            final String traceId,
            final String eventType,
            final String source
    ) {
        Objects.requireNonNull(record, "record is null");
        putIfHasText(record, TRACE_ID, traceId);
        putIfHasText(record, EVENT_TYPE, eventType);
        putIfHasText(record, SOURCE, source);
    }

    /**
     * 키/값이 모두 비어 있지 않을 때만 UTF-8 헤더를 추가합니다.
     *
     * @param record     대상 Kafka ProducerRecord
     * @param headerKey  헤더 키
     * @param headerValue 헤더 값
     */
    public static void putIfHasText(
            final ProducerRecord<String, ?> record,
            final String headerKey,
            final String headerValue
    ) {
        Objects.requireNonNull(record, "record is null");
        if (headerKey == null || headerKey.isBlank() || headerValue == null || headerValue.isBlank()) {
            return;
        }

        record.headers().add(new RecordHeader(
                headerKey.trim(),
                headerValue.trim().getBytes(StandardCharsets.UTF_8)
        ));
    }
}
