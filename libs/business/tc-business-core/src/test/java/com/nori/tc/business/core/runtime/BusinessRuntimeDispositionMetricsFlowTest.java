package com.nori.tc.business.core.runtime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * {@link BusinessRuntimeDispositionMetrics}의 flow 집계 확장 동작을 검증합니다.
 */
class BusinessRuntimeDispositionMetricsFlowTest {

    /**
     * flow + disposition 조합 집계가 정상 누적되는지 검증합니다.
     */
    @Test
    void shouldAggregateCountsByFlowAndDisposition() {
        final BusinessRuntimeDispositionMetrics metrics = new BusinessRuntimeDispositionMetrics();

        metrics.increment("EQP_EVENT", BusinessRuntimeDisposition.ACCEPTED);
        metrics.increment("EQP_EVENT", BusinessRuntimeDisposition.ACCEPTED);
        metrics.increment("MES_EVENT", BusinessRuntimeDisposition.DLQ);
        metrics.increment(BusinessRuntimeDisposition.REJECTED);

        Assertions.assertEquals(2L, metrics.count("EQP_EVENT", BusinessRuntimeDisposition.ACCEPTED));
        Assertions.assertEquals(1L, metrics.count("MES_EVENT", BusinessRuntimeDisposition.DLQ));
        Assertions.assertEquals(1L, metrics.count("UNKNOWN", BusinessRuntimeDisposition.REJECTED));
        Assertions.assertEquals(2L, metrics.count(BusinessRuntimeDisposition.ACCEPTED));
        Assertions.assertEquals(1L, metrics.count(BusinessRuntimeDisposition.DLQ));
        Assertions.assertEquals(1L, metrics.count(BusinessRuntimeDisposition.REJECTED));
    }

    /**
     * flow 스냅샷 키 포맷이 FLOW:DISPOSITION으로 유지되는지 검증합니다.
     */
    @Test
    void shouldExposeSnapshotByFlowDisposition() {
        final BusinessRuntimeDispositionMetrics metrics = new BusinessRuntimeDispositionMetrics();
        metrics.increment("UI_EVENT", BusinessRuntimeDisposition.ACCEPTED);

        final Map<String, Long> snapshot = metrics.snapshotByFlowDisposition();
        Assertions.assertTrue(snapshot.containsKey("UI_EVENT:ACCEPTED"));
        Assertions.assertEquals(1L, snapshot.get("UI_EVENT:ACCEPTED"));
    }
}
