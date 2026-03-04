package com.nori.tc.ui.core.registry;

import com.nori.tc.ui.core.port.redis.DualResponseRedisPort;
import com.nori.tc.ui.domain.task.UiTaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * eqp_create / eqp_update / eqp_delete 요청의 양방향 응답 수집 레지스트리입니다.
 *
 * <p>설계 목표:</p>
 * <ul>
 *   <li>로컬 메모리만 사용하던 단일 인스턴스 제약을 제거합니다.</li>
 *   <li>응답 상태는 Redis에 저장하고, 로컬에는 현재 인스턴스의 대기 Future만 유지합니다.</li>
 *   <li>Redis Pub/Sub 완료 신호를 받아 해당 traceId Future를 완료합니다.</li>
 * </ul>
 */
@Component
public class DualResponseRegistry {

    private static final Logger log = LoggerFactory.getLogger(DualResponseRegistry.class);

    /** Gateway가 tc.ui.commands에 발행하는 응답 source 값 */
    public static final String SOURCE_GATEWAY = "TC-COMM-GATEWAY";

    /** Business Core가 tc.ui.commands에 발행하는 응답 source 값 */
    public static final String SOURCE_BUSINESS = "TC-BUSINESS-CORE";

    /**
     * 로컬 인스턴스 대기 Future 맵입니다.
     *
     * <p>분산 상태는 Redis에 저장하므로, 이 맵은 "현재 인스턴스가 처리 중인 HTTP 요청"의
     * 응답 대기 핸들만 보관합니다.</p>
     */
    private final ConcurrentHashMap<String, CompletableFuture<UiDualTaskFinalResult>> pendingFutures =
            new ConcurrentHashMap<>();

    private final DualResponseRedisPort dualResponseRedisPort;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param dualResponseRedisPort DualResponse Redis 상태 저장 포트
     */
    public DualResponseRegistry(final DualResponseRedisPort dualResponseRedisPort) {
        this.dualResponseRedisPort = Objects.requireNonNull(dualResponseRedisPort,
                "dualResponseRedisPort is null");
    }

    /**
     * traceId 기준 DualResponse 대기를 등록하고 Future를 반환합니다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>Redis에 traceId 대기 상태를 먼저 등록합니다.</li>
     *   <li>로컬 pendingFutures에 CompletableFuture를 등록합니다.</li>
     *   <li>timeoutMs 기반 타임아웃을 적용합니다.</li>
     * </ol>
     *
     * @param traceId 추적 ID
     * @param timeoutMs 타임아웃(ms)
     * @return 최종 결과 Future
     */
    public CompletableFuture<UiDualTaskFinalResult> register(final String traceId, final long timeoutMs) {
        Objects.requireNonNull(traceId, "traceId is null");

        dualResponseRedisPort.register(traceId, timeoutMs);

        final CompletableFuture<UiDualTaskFinalResult> rawFuture = new CompletableFuture<>();
        pendingFutures.put(traceId, rawFuture);

        log.debug("DualResponse 등록 완료. traceId={}, timeoutMs={}, pendingCount={}",
                traceId, timeoutMs, pendingFutures.size());

        final CompletableFuture<UiDualTaskFinalResult> timedFuture =
                rawFuture.orTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        timedFuture.whenComplete((result, throwable) -> {
            pendingFutures.remove(traceId, rawFuture);

            if (throwable instanceof TimeoutException) {
                // 타임아웃이 발생하면 Redis 상태도 정리하여 고아 키 누적을 방지합니다.
                dualResponseRedisPort.cancel(traceId);
                log.warn("DualResponse 타임아웃. traceId={}, timeoutMs={}", traceId, timeoutMs);
                return;
            }

            if (throwable instanceof CancellationException) {
                log.warn("DualResponse 취소됨. traceId={}", traceId);
                return;
            }

            if (throwable != null) {
                log.error("DualResponse 비정상 종료. traceId={}, error={}",
                        traceId, throwable.getMessage(), throwable);
                return;
            }

            log.debug("DualResponse 완료. traceId={}, success={}", traceId, result.success());
        });

        return timedFuture;
    }

    /**
     * 수신된 응답을 Redis 상태에 기록합니다.
     *
     * <p>실제 최종 완료 판단은 Redis 상태(양쪽 응답 존재) 기준으로 수행됩니다.</p>
     *
     * @param traceId 추적 ID
     * @param source 응답 source
     * @param result 응답 결과
     */
    public void record(final String traceId, final String source, final UiTaskResult result) {
        Objects.requireNonNull(traceId, "traceId is null");
        Objects.requireNonNull(source, "source is null");
        Objects.requireNonNull(result, "result is null");

        dualResponseRedisPort.record(traceId, source, result);
    }

    /**
     * 발행 실패 등 비정상 흐름에서 traceId 대기를 강제 취소합니다.
     *
     * <p>HTTP 요청 스레드에서 즉시 실패 응답을 내려야 하는 경우 사용합니다.</p>
     *
     * @param traceId 취소할 traceId
     */
    public void cancel(final String traceId) {
        Objects.requireNonNull(traceId, "traceId is null");

        dualResponseRedisPort.cancel(traceId);

        final CompletableFuture<UiDualTaskFinalResult> localFuture = pendingFutures.remove(traceId);
        if (localFuture != null && !localFuture.isDone()) {
            localFuture.cancel(true);
            log.debug("DualResponse 로컬 Future 취소 완료. traceId={}", traceId);
        }
    }

    /**
     * Redis Pub/Sub 완료 신호를 받아 로컬 Future를 완료합니다.
     *
     * <p>다른 인스턴스에서 record()가 완료되어 완료 채널에 traceId가 발행될 수 있으므로,
     * 현재 인스턴스에 해당 traceId Future가 있으면 Redis에서 최종 결과를 조회해 완료합니다.</p>
     *
     * @param traceId 완료 신호를 받은 traceId
     */
    public void completeFromRedis(final String traceId) {
        Objects.requireNonNull(traceId, "traceId is null");

        final CompletableFuture<UiDualTaskFinalResult> localFuture = pendingFutures.get(traceId);
        if (localFuture == null) {
            if (log.isDebugEnabled()) {
                log.debug("완료 신호 수신했지만 로컬 대기 Future 없음. traceId={}", traceId);
            }
            return;
        }

        final Optional<UiDualTaskFinalResult> resultOpt = dualResponseRedisPort.getResult(traceId);
        if (resultOpt.isEmpty()) {
            log.warn("완료 신호 수신했지만 Redis 결과 미완성. traceId={}", traceId);
            return;
        }

        final boolean completed = localFuture.complete(resultOpt.get());
        if (completed) {
            log.debug("Redis 완료 신호로 Future 완료. traceId={}", traceId);
        }
    }

    /**
     * 현재 인스턴스 기준 대기 Future 개수를 반환합니다.
     *
     * @return pending future 개수
     */
    public int pendingCount() {
        return pendingFutures.size();
    }
}
