package com.nori.tc.comm.adapters.kafka.ui;

import com.nori.tc.comm.gateway.lifecycle.model.EquipmentLifecycleOutcome;
import com.nori.tc.comm.gateway.lifecycle.port.EquipmentLifecycleOutcomeListener;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskReplyPublisher;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UI START/END 요청의 지연 응답(deferred reply)을 관리하는 서비스입니다.
 *
 * <p>핵심 목적:</p>
 * <p>1) UI 요청 수신 시 eqpId 단위 pending 상태를 등록합니다.</p>
 * <p>2) lifecycle 상태머신 outcome(APPLIED/FAILED)을 받아 최종 START_REP/END_REP를 발행합니다.</p>
 * <p>3) 요청 처리 단계에서 실패한 pending은 즉시 정리해 stale 응답을 방지합니다.</p>
 */
@Service
public class GatewayUiDeferredLifecycleReplyService implements EquipmentLifecycleOutcomeListener {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiDeferredLifecycleReplyService.class);

    /**
     * eqpId 기준 단일 pending 요청 저장소입니다.
     */
    private final ConcurrentHashMap<String, PendingUiLifecycleReply> pendingByEqpId = new ConcurrentHashMap<>();

    private final KafkaTaskReplyPublisher<KafkaUiTaskMessage> replyPublisher;

    /**
     * 지연 응답 서비스를 초기화합니다.
     *
     * @param replyPublisher UI reply 발행 포트
     */
    public GatewayUiDeferredLifecycleReplyService(
            final KafkaTaskReplyPublisher<KafkaUiTaskMessage> replyPublisher
    ) {
        this.replyPublisher = Objects.requireNonNull(replyPublisher, "replyPublisher is null");
    }

    /**
     * START 요청의 pending 응답 상태를 등록합니다.
     *
     * @param message 원본 UI 요청
     * @param replyEventType 응답 이벤트 타입(EQP_START_REP)
     * @param timeoutMs 요청 timeout(ms), 진단 로그 용도
     */
    public void registerStartPending(
            final KafkaUiTaskMessage message,
            final String replyEventType,
            final long timeoutMs
    ) {
        registerPending(message, replyEventType, TransitionType.START, timeoutMs);
    }

    /**
     * END 요청의 pending 응답 상태를 등록합니다.
     *
     * @param message 원본 UI 요청
     * @param replyEventType 응답 이벤트 타입(EQP_END_REP)
     * @param timeoutMs 요청 timeout(ms), 진단 로그 용도
     */
    public void registerEndPending(
            final KafkaUiTaskMessage message,
            final String replyEventType,
            final long timeoutMs
    ) {
        registerPending(message, replyEventType, TransitionType.END, timeoutMs);
    }

    /**
     * 요청 처리 단계에서 예외가 발생한 pending을 정리합니다.
     *
     * <p>요청 실패 시 즉시 FAIL 응답은 공통 파이프라인이 처리하므로,
     * 이 메서드는 pending 상태만 정리합니다.</p>
     *
     * @param eqpId 대상 설비 ID
     * @param traceId 요청 traceId
     * @param transitionType 요청 전이 타입
     * @param reason 정리 사유
     */
    public void clearPendingOnRequestFailure(
            final String eqpId,
            final String traceId,
            final TransitionType transitionType,
            final String reason
    ) {
        final String normalizedEqpId = normalizeRequiredText(eqpId, "eqpId");
        final String normalizedTraceId = normalizeTraceId(traceId);
        final PendingUiLifecycleReply pending = pendingByEqpId.get(normalizedEqpId);
        if (pending == null) {
            return;
        }
        if (pending.transitionType() != transitionType) {
            return;
        }
        if (!pending.traceId().equals(normalizedTraceId)) {
            return;
        }
        if (pendingByEqpId.remove(normalizedEqpId, pending)) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Deferred lifecycle pending cleared by request failure. eqpId={}, transition={}, traceId={}, reason={}",
                        normalizedEqpId,
                        transitionType,
                        normalizedTraceId,
                        reason
                );
            }
        }
    }

    /**
     * lifecycle 전이 결과를 수신해 pending 요청의 최종 응답을 발행합니다.
     *
     * @param outcome lifecycle 전이 확정 결과
     */
    @Override
    public void onOutcome(final EquipmentLifecycleOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome is null");
        final PendingUiLifecycleReply pending = pendingByEqpId.get(outcome.eqpId());
        if (pending == null) {
            if (log.isDebugEnabled()) {
                log.debug("Deferred lifecycle outcome ignored (no pending). eqpId={}, transition={}, status={}, reason={}",
                        outcome.eqpId(),
                        outcome.transition(),
                        outcome.status(),
                        outcome.reason());
            }
            return;
        }

        if (!isMatchingTransition(pending, outcome)) {
            if (log.isDebugEnabled()) {
                log.debug("Deferred lifecycle outcome ignored (transition mismatch). eqpId={}, pendingTransition={}, outcomeTransition={}, status={}, reason={}",
                        outcome.eqpId(),
                        pending.transitionType(),
                        outcome.transition(),
                        outcome.status(),
                        outcome.reason());
            }
            return;
        }

        if (!pendingByEqpId.remove(outcome.eqpId(), pending)) {
            if (log.isDebugEnabled()) {
                log.debug("Deferred lifecycle outcome ignored (pending changed). eqpId={}, transition={}, status={}, reason={}",
                        outcome.eqpId(),
                        outcome.transition(),
                        outcome.status(),
                        outcome.reason());
            }
            return;
        }

        if (outcome.status() == EquipmentLifecycleOutcome.Status.APPLIED) {
            publishPendingResult(
                    pending,
                    KafkaTaskResult.pass(),
                    "LIFECYCLE_APPLIED:" + safeReason(outcome.reason())
            );
            return;
        }

        final KafkaTaskResult failedResult = switch (outcome.transition()) {
            case START -> KafkaTaskResult.fail(
                    mapStartFailureCode(outcome.reason()),
                    "START transition failed. reason=" + safeReason(outcome.reason())
            );
            case END -> KafkaTaskResult.fail(
                    mapEndFailureCode(outcome.reason()),
                    "END transition failed. reason=" + safeReason(outcome.reason())
            );
        };

        publishPendingResult(
                pending,
                failedResult,
                "LIFECYCLE_FAILED:" + safeReason(outcome.reason())
        );
    }

    /**
     * pending 등록을 수행합니다.
     *
     * @param message 원본 UI 메시지
     * @param replyEventType 응답 이벤트 타입
     * @param transitionType 전이 타입
     * @param timeoutMs 요청 timeout(ms)
     */
    private void registerPending(
            final KafkaUiTaskMessage message,
            final String replyEventType,
            final TransitionType transitionType,
            final long timeoutMs
    ) {
        Objects.requireNonNull(message, "message is null");
        final String normalizedEqpId = normalizeRequiredText(
                message.data() == null ? null : message.data().eqpId(),
                "eqpId"
        );
        final String normalizedTraceId = normalizeTraceId(
                message.metadata() == null ? null : message.metadata().traceId()
        );
        final String normalizedReplyEventType = normalizeRequiredText(replyEventType, "replyEventType");

        final PendingUiLifecycleReply newPending = new PendingUiLifecycleReply(
                normalizedEqpId,
                normalizedTraceId,
                transitionType,
                normalizedReplyEventType,
                message,
                timeoutMs,
                System.currentTimeMillis()
        );

        final PendingUiLifecycleReply existing = pendingByEqpId.putIfAbsent(normalizedEqpId, newPending);
        if (existing != null) {
            if (transitionType == TransitionType.END && existing.transitionType() == TransitionType.START) {
                throw new GatewayUiRuntimeControlService.ProcessingException(
                        GatewayUiRuntimeControlService.ErrorCode.EQP_START_NOT_COMPLETED,
                        "START transition is not completed yet"
                );
            }
            throw new GatewayUiRuntimeControlService.ProcessingException(
                    GatewayUiRuntimeControlService.ErrorCode.EQP_LIFECYCLE_BUSY,
                    "Another lifecycle transition is pending"
            );
        }

        log.info("Deferred lifecycle pending registered. eqpId={}, transition={}, traceId={}, replyEventType={}, timeoutMs={}",
                normalizedEqpId,
                transitionType,
                normalizedTraceId,
                normalizedReplyEventType,
                timeoutMs);
    }

    /**
     * pending 요청의 최종 응답을 UI 토픽으로 발행합니다.
     *
     * @param pending pending 요청 정보
     * @param result 최종 결과
     * @param reason 발행 사유
     */
    private void publishPendingResult(
            final PendingUiLifecycleReply pending,
            final KafkaTaskResult result,
            final String reason
    ) {
        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(pending.eqpId(), pending.traceId())) {
            replyPublisher.publishResult(pending.request(), pending.replyEventType(), result);
            log.info("Deferred lifecycle reply published. eqpId={}, transition={}, traceId={}, replyEventType={}, status={}, reason={}",
                    pending.eqpId(),
                    pending.transitionType(),
                    pending.traceId(),
                    pending.replyEventType(),
                    result.status(),
                    reason);
        } catch (Exception ex) {
            log.error("Deferred lifecycle reply publish failed. eqpId={}, transition={}, traceId={}, replyEventType={}, status={}, reason={}",
                    pending.eqpId(),
                    pending.transitionType(),
                    pending.traceId(),
                    pending.replyEventType(),
                    result.status(),
                    reason,
                    ex);
        }
    }

    /**
     * START 실패 사유를 UI 오류 코드로 매핑합니다.
     *
     * @param reason lifecycle 실패 사유
     * @return UI errorCode
     */
    private String mapStartFailureCode(final String reason) {
        final String normalizedReason = safeReason(reason);
        return switch (normalizedReason) {
            case "OUTBOUND_RETRY_EXHAUSTED" -> GatewayUiRuntimeControlService.ErrorCode.EQP_START_RETRY_EXHAUSTED;
            case "START_TIMEOUT" -> GatewayUiRuntimeControlService.ErrorCode.EQP_START_TIMEOUT;
            default -> GatewayUiRuntimeControlService.ErrorCode.EQP_START_TIMEOUT;
        };
    }

    /**
     * END 실패 사유를 UI 오류 코드로 매핑합니다.
     *
     * @param reason lifecycle 실패 사유
     * @return UI errorCode
     */
    private String mapEndFailureCode(final String reason) {
        final String normalizedReason = safeReason(reason);
        if ("END_TIMEOUT".equals(normalizedReason)) {
            return GatewayUiRuntimeControlService.ErrorCode.EQP_END_TIMEOUT;
        }
        return GatewayUiRuntimeControlService.ErrorCode.EQP_END_TIMEOUT;
    }

    /**
     * pending 전이 타입과 outcome 전이 타입 일치 여부를 검증합니다.
     */
    private boolean isMatchingTransition(
            final PendingUiLifecycleReply pending,
            final EquipmentLifecycleOutcome outcome
    ) {
        return switch (outcome.transition()) {
            case START -> pending.transitionType() == TransitionType.START;
            case END -> pending.transitionType() == TransitionType.END;
        };
    }

    /**
     * 필수 문자열을 정규화합니다.
     */
    private static String normalizeRequiredText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    /**
     * traceId를 정규화합니다.
     */
    private static String normalizeTraceId(final String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return "N/A";
        }
        return traceId.trim();
    }

    /**
     * 사유 문자열을 안전하게 정규화합니다.
     */
    private static String safeReason(final String reason) {
        if (reason == null || reason.isBlank()) {
            return "N/A";
        }
        return reason.trim();
    }

    /**
     * pending 요청 전이 타입입니다.
     */
    public enum TransitionType {
        START,
        END
    }

    /**
     * eqpId 단위 pending 지연 응답 메타데이터입니다.
     */
    private record PendingUiLifecycleReply(
            String eqpId,
            String traceId,
            TransitionType transitionType,
            String replyEventType,
            KafkaUiTaskMessage request,
            long timeoutMs,
            long registeredAtEpochMs
    ) {
    }
}

