package com.nori.tc.business.core.runtime;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Business Runtime disposition 계측기입니다.
 *
 * <p>목적:</p>
 * <p>1) ACCEPTED/RETRY/DLQ/REJECTED 건수를 공통 방식으로 누적</p>
 * <p>2) 운영 대시보드/헬스 점검 시 스냅샷 조회 기반 제공</p>
 *
 * <p>구현 메모:</p>
 * <p>- 상태값이 enum으로 고정되어 있어 EnumMap + LongAdder 조합을 사용합니다.</p>
 * <p>- LongAdder는 다중 스레드 누적 성능이 좋아 런타임 worker 환경에 적합합니다.</p>
 */
@Component
public class BusinessRuntimeDispositionMetrics {

    /**
     * flow 값이 비어있는 경우 사용할 기본 식별자입니다.
     */
    private static final String FLOW_UNKNOWN = "UNKNOWN";

    /**
     * disposition별 누적 카운터입니다.
     */
    private final Map<BusinessRuntimeDisposition, LongAdder> counters =
            new EnumMap<>(BusinessRuntimeDisposition.class);

    /**
     * flow + disposition 조합별 누적 카운터입니다.
     *
     * <p>키 포맷: {@code FLOW:DISPOSITION}</p>
     */
    private final Map<String, LongAdder> flowCounters = new ConcurrentHashMap<>();

    /**
     * 기본 카운터 맵을 초기화합니다.
     */
    public BusinessRuntimeDispositionMetrics() {
        for (BusinessRuntimeDisposition disposition : BusinessRuntimeDisposition.values()) {
            counters.put(disposition, new LongAdder());
        }
    }

    /**
     * disposition 카운터를 1 증가시킵니다.
     *
     * @param disposition 증가 대상 disposition
     */
    public void increment(final BusinessRuntimeDisposition disposition) {
        increment(FLOW_UNKNOWN, disposition);
    }

    /**
     * flow + disposition 조합 카운터를 1 증가시킵니다.
     *
     * <p>집계 목적:</p>
     * <p>1) EQP/MES/UI 경로별 disposition 추세를 분리 관찰</p>
     * <p>2) 운영 대시보드에서 flow 단위 이상 징후를 빠르게 식별</p>
     *
     * @param flow 처리 흐름 식별자 (예: EQP_EVENT, MES_EVENT, UI_EVENT)
     * @param disposition 증가 대상 disposition
     */
    public void increment(final String flow, final BusinessRuntimeDisposition disposition) {
        if (disposition == null) {
            return;
        }
        final LongAdder adder = counters.get(disposition);
        if (adder != null) {
            adder.increment();
        }
        final String metricKey = toMetricKey(flow, disposition);
        flowCounters.computeIfAbsent(metricKey, ignored -> new LongAdder()).increment();
    }

    /**
     * 특정 disposition의 누적 건수를 반환합니다.
     *
     * @param disposition 조회 대상 disposition
     * @return 누적 건수
     */
    public long count(final BusinessRuntimeDisposition disposition) {
        if (disposition == null) {
            return 0L;
        }
        final LongAdder adder = counters.get(disposition);
        return adder == null ? 0L : adder.sum();
    }

    /**
     * 특정 flow + disposition 조합의 누적 건수를 반환합니다.
     *
     * @param flow 처리 흐름 식별자
     * @param disposition 조회 대상 disposition
     * @return 누적 건수
     */
    public long count(final String flow, final BusinessRuntimeDisposition disposition) {
        if (disposition == null) {
            return 0L;
        }
        final LongAdder adder = flowCounters.get(toMetricKey(flow, disposition));
        return adder == null ? 0L : adder.sum();
    }

    /**
     * 현재 disposition 누적 상태를 불변 스냅샷으로 반환합니다.
     *
     * @return disposition -> count 맵
     */
    public Map<BusinessRuntimeDisposition, Long> snapshot() {
        final Map<BusinessRuntimeDisposition, Long> snapshot = new EnumMap<>(BusinessRuntimeDisposition.class);
        for (Map.Entry<BusinessRuntimeDisposition, LongAdder> entry : counters.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sum());
        }
        return Map.copyOf(snapshot);
    }

    /**
     * 현재 flow + disposition 누적 상태를 불변 스냅샷으로 반환합니다.
     *
     * <p>반환 키 예시: {@code EQP_EVENT:ACCEPTED}, {@code MES_EVENT:DLQ}</p>
     *
     * @return flow:disposition -> count 맵
     */
    public Map<String, Long> snapshotByFlowDisposition() {
        final Map<String, Long> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, LongAdder> entry : flowCounters.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sum());
        }
        return Map.copyOf(snapshot);
    }

    /**
     * flow + disposition 조합 키를 표준 포맷으로 정규화합니다.
     *
     * @param flow 처리 흐름 식별자
     * @param disposition disposition 값
     * @return 정규화된 metric 키
     */
    private static String toMetricKey(final String flow, final BusinessRuntimeDisposition disposition) {
        final String normalizedFlow = (flow == null || flow.isBlank())
                ? FLOW_UNKNOWN
                : flow.trim().toUpperCase();
        return normalizedFlow + ":" + disposition.name();
    }
}
