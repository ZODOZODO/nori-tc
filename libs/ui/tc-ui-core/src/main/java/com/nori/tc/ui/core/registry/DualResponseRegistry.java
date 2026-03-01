package com.nori.tc.ui.core.registry;

import com.nori.tc.ui.domain.task.UiTaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * eqp_create / eqp_update / eqp_delete 요청에 대한 양방향 응답 수집 레지스트리입니다.
 *
 * <p>역할:</p>
 * <p>UI Backend는 Gateway와 Business Core 양쪽에 동시에 Kafka 메시지를 발행하고,
 * tc.ui.commands 토픽에서 양쪽의 응답을 모두 수신한 후 최종 결과를 판정합니다.
 * 이 레지스트리는 traceId 단위로 {gatewayResult, businessResult} 쌍을 관리하고
 * 양쪽 모두 수신 완료 시 CompletableFuture를 완료시킵니다.</p>
 *
 * <p>단일 인스턴스 운영 전제:</p>
 * <p>CompletableFuture는 JVM 메모리에 저장됩니다. 다중 인스턴스 환경에서는
 * Redis polling 방식으로 전환하는 리팩토링이 필요합니다.</p>
 *
 * <p>응답 출처 식별:</p>
 * <ul>
 *   <li>{@link #SOURCE_GATEWAY}: {@code TC-COMM-GATEWAY}</li>
 *   <li>{@link #SOURCE_BUSINESS}: {@code TC-BUSINESS-CORE}</li>
 * </ul>
 */
@Component
public class DualResponseRegistry {

    private static final Logger log = LoggerFactory.getLogger(DualResponseRegistry.class);

    /**
     * Gateway가 tc.ui.commands에 발행하는 응답의 metadata.source 값입니다.
     */
    public static final String SOURCE_GATEWAY = "TC-COMM-GATEWAY";

    /**
     * Business Core가 tc.ui.commands에 발행하는 응답의 metadata.source 값입니다.
     */
    public static final String SOURCE_BUSINESS = "TC-BUSINESS-CORE";

    // traceId → DualResponseTracker 매핑 (ConcurrentHashMap으로 멀티스레드 안전)
    private final ConcurrentHashMap<String, DualResponseTracker> trackers = new ConcurrentHashMap<>();

    /**
     * 새 DualResponse 대기를 등록하고 Future를 반환합니다.
     *
     * <p>EqpController에서 Kafka 발행 직전에 호출합니다.
     * 반환된 Future는 양쪽 응답 수신 시 완료되거나, timeout 초과 시 TimeoutException으로 완료됩니다.</p>
     *
     * @param traceId   작업 추적 ID (ULID)
     * @param timeoutMs 대기 최대 시간 (밀리초)
     * @return 양방향 응답 최종 결과 Future
     */
    public CompletableFuture<UiDualTaskFinalResult> register(final String traceId, final long timeoutMs) {
        final DualResponseTracker tracker = new DualResponseTracker(traceId);
        trackers.put(traceId, tracker);

        log.debug("DualResponse 등록. traceId={}, timeoutMs={}", traceId, timeoutMs);

        // 타임아웃 설정: 지정 시간 초과 시 TimeoutException으로 Future 완료
        final CompletableFuture<UiDualTaskFinalResult> timedFuture =
                tracker.getFuture().orTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        // 완료(성공/실패/타임아웃) 시 트래커 제거하여 메모리 누수 방지
        timedFuture.whenComplete((result, throwable) -> {
            trackers.remove(traceId, tracker);
            if (throwable instanceof TimeoutException) {
                log.warn("DualResponse 타임아웃. traceId={}, timeoutMs={}, "
                        + "gatewayReceived={}, businessReceived={}",
                        traceId, timeoutMs,
                        tracker.isGatewayReceived(), tracker.isBusinessReceived());
            } else if (throwable != null) {
                log.error("DualResponse 비정상 종료. traceId={}, error={}",
                        traceId, throwable.getMessage(), throwable);
            } else {
                log.debug("DualResponse 완료. traceId={}, success={}", traceId, result.success());
            }
        });

        return timedFuture;
    }

    /**
     * 수신된 응답을 해당 traceId의 트래커에 기록합니다.
     *
     * <p>UiCommandIngressService가 tc.ui.commands 메시지를 수신했을 때 호출합니다.
     * Gateway 응답과 Business 응답이 모두 수신되면 Future를 자동으로 완료시킵니다.</p>
     *
     * @param traceId 작업 추적 ID
     * @param source  응답 출처 ({@link #SOURCE_GATEWAY} 또는 {@link #SOURCE_BUSINESS})
     * @param result  수신된 작업 결과 (PASS/FAIL + 오류 정보)
     */
    public void record(final String traceId, final String source, final UiTaskResult result) {
        final DualResponseTracker tracker = trackers.get(traceId);
        if (tracker == null) {
            // 타임아웃 후 지연 도착한 응답: 무시하고 경고 로그만 기록
            log.warn("등록된 DualResponse 없음 - 타임아웃 후 지연 도착 가능성. traceId={}, source={}",
                    traceId, source);
            return;
        }

        log.debug("DualResponse 응답 수신. traceId={}, source={}, status={}",
                traceId, source, result.status());
        tracker.record(source, result);
    }

    /**
     * 현재 대기 중인 트래커 수를 반환합니다 (모니터링용).
     *
     * @return 현재 등록된 트래커 수
     */
    public int pendingCount() {
        return trackers.size();
    }

    // ---------------------------------------------------------------------------
    // 내부 트래커 클래스
    // ---------------------------------------------------------------------------

    /**
     * traceId 단위의 양방향 응답 수집기입니다.
     *
     * <p>record() 메서드는 synchronized로 보호됩니다:
     * Gateway와 Business 응답이 서로 다른 Kafka Consumer 스레드에서 도착할 수 있으므로
     * 두 결과 필드와 Future 완료 로직을 원자적으로 처리해야 합니다.</p>
     */
    private static final class DualResponseTracker {

        private final String traceId;
        private final CompletableFuture<UiDualTaskFinalResult> future;

        // volatile: 다른 스레드의 읽기에서 최신 값을 볼 수 있도록 보장
        private volatile UiTaskResult gatewayResult;
        private volatile UiTaskResult businessResult;

        DualResponseTracker(final String traceId) {
            this.traceId = traceId;
            this.future = new CompletableFuture<>();
        }

        CompletableFuture<UiDualTaskFinalResult> getFuture() {
            return future;
        }

        boolean isGatewayReceived() {
            return gatewayResult != null;
        }

        boolean isBusinessReceived() {
            return businessResult != null;
        }

        /**
         * 응답을 기록하고, 양쪽 모두 수신되면 Future를 완료합니다.
         *
         * <p>synchronized: 두 응답이 동시에 도착해도 안전하게 처리합니다.</p>
         *
         * @param source 응답 출처
         * @param result 수신 결과
         */
        synchronized void record(final String source, final UiTaskResult result) {
            if (SOURCE_GATEWAY.equals(source)) {
                if (gatewayResult != null) {
                    // 동일 출처에서 중복 응답: 두 번째 응답 무시
                    log.warn("Gateway 응답 중복 수신 - 무시. traceId={}", traceId);
                    return;
                }
                gatewayResult = result;
            } else if (SOURCE_BUSINESS.equals(source)) {
                if (businessResult != null) {
                    log.warn("Business 응답 중복 수신 - 무시. traceId={}", traceId);
                    return;
                }
                businessResult = result;
            } else {
                log.warn("알 수 없는 응답 출처. traceId={}, source={}", traceId, source);
                return;
            }

            // 양쪽 모두 수신된 경우 최종 결과 판정 및 Future 완료
            if (gatewayResult != null && businessResult != null) {
                resolveNow();
            }
        }

        /**
         * 최종 결과를 판정하고 Future를 완료합니다.
         *
         * <p>판정 규칙: 양쪽 모두 PASS여야 최종 PASS, 한쪽이라도 FAIL이면 최종 FAIL.</p>
         */
        private void resolveNow() {
            final boolean allPass = gatewayResult.isSuccess() && businessResult.isSuccess();
            final UiDualTaskFinalResult finalResult =
                    new UiDualTaskFinalResult(traceId, allPass, gatewayResult, businessResult);

            if (!allPass) {
                // 부분 실패 여부를 로그에 기록하여 운영자가 보정 작업을 인지할 수 있도록 함
                final boolean partialFailure = finalResult.hasPartialFailure();
                log.warn("DualResponse 최종 FAIL. traceId={}, partialFailure={}, "
                        + "gatewayStatus={}, businessStatus={}",
                        traceId, partialFailure,
                        gatewayResult.status(), businessResult.status());
            }

            future.complete(finalResult);
        }
    }
}
