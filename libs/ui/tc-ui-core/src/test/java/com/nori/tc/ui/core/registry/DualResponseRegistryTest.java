package com.nori.tc.ui.core.registry;

import com.nori.tc.ui.core.port.redis.DualResponseRedisPort;
import com.nori.tc.ui.domain.task.UiTaskResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DualResponseRegistry}의 완료/정리 경합 방지 동작을 검증합니다.
 */
class DualResponseRegistryTest {

    /**
     * 정상 완료 경로에서 pending 엔트리가 반드시 정리되는지 검증합니다.
     */
    @Test
    @DisplayName("정상 완료 후 pendingCount는 0이어야 한다")
    void 정상완료후_pendingCount_0() throws Exception {
        final InMemoryDualResponseRedisPort redisPort = new InMemoryDualResponseRedisPort();
        final DualResponseRegistry registry = new DualResponseRegistry(redisPort);

        final String traceId = "trace-success-001";
        final CompletableFuture<UiDualTaskFinalResult> future = registry.register(traceId, 3_000L);
        assertTrue(registry.isPending(traceId));

        final UiTaskResult gateway = UiTaskResult.pass(traceId, DualResponseRegistry.SOURCE_GATEWAY);
        final UiTaskResult business = UiTaskResult.pass(traceId, DualResponseRegistry.SOURCE_BUSINESS);
        redisPort.results.put(traceId, new UiDualTaskFinalResult(traceId, true, gateway, business));

        registry.completeFromRedis(traceId);

        final UiDualTaskFinalResult result = future.get(1, TimeUnit.SECONDS);
        assertTrue(result.success());
        assertEquals(0, registry.pendingCount());
        assertFalse(registry.isPending(traceId));
    }

    /**
     * 타임아웃 경로에서 pending 정리와 Redis cancel 호출이 수행되는지 검증합니다.
     */
    @Test
    @DisplayName("타임아웃 후 pendingCount는 0이고 Redis cancel이 호출되어야 한다")
    void 타임아웃후_pendingCount_0_and_redisCancel() {
        final InMemoryDualResponseRedisPort redisPort = new InMemoryDualResponseRedisPort();
        final DualResponseRegistry registry = new DualResponseRegistry(redisPort);

        final String traceId = "trace-timeout-001";
        final CompletableFuture<UiDualTaskFinalResult> future = registry.register(traceId, 50L);

        final ExecutionException executionException = assertThrows(
                ExecutionException.class,
                () -> future.get(2, TimeUnit.SECONDS)
        );
        assertTrue(executionException.getCause() instanceof TimeoutException);

        assertEquals(0, registry.pendingCount());
        assertTrue(redisPort.cancelledTraceIds.contains(traceId));
    }

    /**
     * 취소 경로에서 pending 정리가 보장되는지 검증합니다.
     */
    @Test
    @DisplayName("cancel 호출 후 pendingCount는 0이어야 한다")
    void cancel후_pendingCount_0() {
        final InMemoryDualResponseRedisPort redisPort = new InMemoryDualResponseRedisPort();
        final DualResponseRegistry registry = new DualResponseRegistry(redisPort);

        final String traceId = "trace-cancel-001";
        final CompletableFuture<UiDualTaskFinalResult> future = registry.register(traceId, 3_000L);

        registry.cancel(traceId);

        assertTrue(future.isCancelled());
        assertEquals(0, registry.pendingCount());
        assertTrue(redisPort.cancelledTraceIds.contains(traceId));
    }

    /**
     * 동일 traceId에 완료 신호가 중복 도착해도 단일 완료로 처리되는지 검증합니다.
     */
    @Test
    @DisplayName("중복 completeFromRedis 호출은 단일 완료로 수렴해야 한다")
    void 중복완료_단일완료수렴() throws Exception {
        final InMemoryDualResponseRedisPort redisPort = new InMemoryDualResponseRedisPort();
        final DualResponseRegistry registry = new DualResponseRegistry(redisPort);

        final String traceId = "trace-dup-complete-001";
        final CompletableFuture<UiDualTaskFinalResult> future = registry.register(traceId, 3_000L);

        final UiTaskResult gateway = UiTaskResult.pass(traceId, DualResponseRegistry.SOURCE_GATEWAY);
        final UiTaskResult business = UiTaskResult.pass(traceId, DualResponseRegistry.SOURCE_BUSINESS);
        redisPort.results.put(traceId, new UiDualTaskFinalResult(traceId, true, gateway, business));

        registry.completeFromRedis(traceId);
        registry.completeFromRedis(traceId);

        final UiDualTaskFinalResult result = future.get(1, TimeUnit.SECONDS);
        assertTrue(result.success());
        assertEquals(0, registry.pendingCount());
    }

    /**
     * 테스트 전용 Redis 포트 인메모리 구현입니다.
     */
    private static final class InMemoryDualResponseRedisPort implements DualResponseRedisPort {

        private final ConcurrentHashMap<String, UiDualTaskFinalResult> results = new ConcurrentHashMap<>();
        private final Set<String> cancelledTraceIds = ConcurrentHashMap.newKeySet();

        @Override
        public void register(final String traceId, final long timeoutMs) {
            // 테스트에서는 별도 상태를 저장하지 않습니다.
        }

        @Override
        public void record(final String traceId, final String source, final UiTaskResult result) {
            // 테스트에서는 completeFromRedis 직접 호출로 완료를 시뮬레이션합니다.
        }

        @Override
        public void cancel(final String traceId) {
            cancelledTraceIds.add(traceId);
        }

        @Override
        public Optional<UiDualTaskFinalResult> getResult(final String traceId) {
            return Optional.ofNullable(results.get(traceId));
        }
    }
}

