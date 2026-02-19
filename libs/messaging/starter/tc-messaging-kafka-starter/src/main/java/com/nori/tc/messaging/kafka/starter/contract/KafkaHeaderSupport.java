package com.nori.tc.messaging.kafka.starter.contract;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka ProducerRecord header 공통 유틸입니다.
 *
 * <p>앱별 발행 코드에서 헤더 처리 로직이 중복되지 않도록
 * UTF-8 직렬화/공통 키 규칙을 한 곳에서 제공합니다.</p>
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
     * 발행자(source) 헤더 키입니다.
     */
    public static final String SOURCE = "x-source";

    private KafkaHeaderSupport() {
        // utility class
    }

    /**
     * 문자열 헤더 맵을 ProducerRecord 헤더로 복사합니다.
     *
     * @param record 대상 producer record
     * @param headers 복사할 헤더 맵
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
     * 공통 추적 헤더(trace/eventType/source)를 한 번에 추가합니다.
     *
     * @param record 대상 producer record
     * @param traceId trace 식별자
     * @param eventType 이벤트 타입
     * @param source 발행자 식별자
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
     * 값이 비어 있지 않을 때만 UTF-8 바이트 헤더를 추가합니다.
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
        record.headers().add(new RecordHeader(headerKey.trim(), headerValue.trim().getBytes(StandardCharsets.UTF_8)));
    }
}
