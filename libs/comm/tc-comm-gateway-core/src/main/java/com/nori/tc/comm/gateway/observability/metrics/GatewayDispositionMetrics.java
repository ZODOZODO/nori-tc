package com.nori.tc.comm.gateway.observability.metrics;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Gateway disposition 집계 컴포넌트입니다.
 *
 * <p>핵심 목적:</p>
 * <p>1) flow(UI_TASK/COMMAND 등) + disposition 조합별 카운터 누적</p>
 * <p>2) DLQ/REJECTED 관측을 대시보드에서 즉시 조회할 수 있는 스냅샷 제공</p>
 *
 * <p>메트릭 키 포맷: {@code FLOW:DISPOSITION}</p>
 */
@Component
public class GatewayDispositionMetrics {

    /**
     * flow:disposition 단위 카운터 저장소입니다.
     */
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    /**
     * 지정한 flow/disposition 조합 카운터를 1 증가시킵니다.
     *
     * @param flow 처리 흐름 식별자(예: UI_TASK, COMMAND)
     * @param disposition 표준 disposition
     */
    public void increment(final String flow, final GatewayDisposition disposition) {
        final String key = toMetricKey(flow, disposition);
        counters.computeIfAbsent(key, ignored -> new LongAdder()).increment();
    }

    /**
     * 지정한 flow/disposition 조합 누적 건수를 조회합니다.
     *
     * @param flow 처리 흐름 식별자
     * @param disposition 표준 disposition
     * @return 누적 건수
     */
    public long count(final String flow, final GatewayDisposition disposition) {
        final String key = toMetricKey(flow, disposition);
        final LongAdder adder = counters.get(key);
        return adder == null ? 0L : adder.sum();
    }

    /**
     * 전체 disposition 카운터 스냅샷을 반환합니다.
     *
     * <p>반환 맵 키 예시: {@code UI_TASK:REJECTED}, {@code COMMAND:DLQ}</p>
     *
     * @return 불변 스냅샷 맵
     */
    public Map<String, Long> snapshot() {
        final Map<String, Long> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, LongAdder> entry : counters.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sum());
        }
        return Map.copyOf(snapshot);
    }

    /**
     * flow/disposition을 메트릭 키 문자열로 정규화합니다.
     */
    private static String toMetricKey(final String flow, final GatewayDisposition disposition) {
        final String normalizedFlow = (flow == null || flow.isBlank())
                ? "UNKNOWN"
                : flow.trim().toUpperCase();
        final GatewayDisposition normalizedDisposition = disposition == null
                ? GatewayDisposition.REJECTED
                : disposition;
        return normalizedFlow + ":" + normalizedDisposition.name();
    }
}

