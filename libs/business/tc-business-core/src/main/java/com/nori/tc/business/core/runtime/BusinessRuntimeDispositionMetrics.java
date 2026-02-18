package com.nori.tc.business.core.runtime;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
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
     * disposition별 누적 카운터입니다.
     */
    private final Map<BusinessRuntimeDisposition, LongAdder> counters =
            new EnumMap<>(BusinessRuntimeDisposition.class);

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
        if (disposition == null) {
            return;
        }
        final LongAdder adder = counters.get(disposition);
        if (adder != null) {
            adder.increment();
        }
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
}

