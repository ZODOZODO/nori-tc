package com.nori.tc.ui.core.service;

import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyMessage;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyStatus;
import com.nori.tc.ui.core.port.messaging.UiCommandIngressPort;
import com.nori.tc.ui.core.port.redis.AsyncResultStorePort;
import com.nori.tc.ui.core.registry.DualResponseRegistry;
import com.nori.tc.ui.domain.task.UiTaskResult;
import com.nori.tc.ui.domain.task.UiTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * tc.ui.commands 토픽 수신 메시지 처리 서비스입니다.
 *
 * <p>역할:</p>
 * <p>{@link UiCommandIngressPort}의 구현체로, tc-ui-kafka-adapter의
 * UiCommandKafkaSubscriber가 Kafka에서 수신한 메시지를 넘겨주면
 * eventType에 따라 적절한 핸들러로 라우팅합니다.</p>
 *
 * <p>라우팅 규칙:</p>
 * <ul>
 *   <li>EQP_CREATE / EQP_UPDATE / EQP_DELETE → {@link DualResponseRegistry}:
 *       Gateway와 Business Core 양쪽 응답을 수집하여 DeferredResult 완료 처리</li>
 *   <li>EQP_START / EQP_END → {@link AsyncResultStorePort}:
 *       Gateway 단일 응답을 Redis에 저장, front가 polling으로 확인</li>
 * </ul>
 *
 * <p>TODO (Phase 9에서 확인 필요):</p>
 * <p>기존 {@code GatewayUiDeferredLifecycleReplyService}가 EQP_START 응답을
 * tc.ui.commands에 발행할 때 eventType이 {@code EQP_START}인지 {@code EQP_START_REP}인지
 * 확인 후 분기 로직을 조정하십시오. 새 eventType이면 KafkaUiTaskEventType에 상수 추가도 필요합니다.</p>
 */
@Component
public class UiCommandIngressService implements UiCommandIngressPort {

    private static final Logger log = LoggerFactory.getLogger(UiCommandIngressService.class);

    // eqp_create/update/delete reply: Gateway + Business 양쪽에서 응답
    private static final String EVENT_EQP_CREATE = "EQP_CREATE";
    private static final String EVENT_EQP_UPDATE = "EQP_UPDATE";
    private static final String EVENT_EQP_DELETE = "EQP_DELETE";

    // eqp_start/end reply: Gateway 단일 응답 (TODO: Phase 9에서 EQP_START_REP 여부 확인)
    private static final String EVENT_EQP_START = "EQP_START";
    private static final String EVENT_EQP_END   = "EQP_END";

    private final DualResponseRegistry dualResponseRegistry;
    private final AsyncResultStorePort asyncResultStorePort;

    public UiCommandIngressService(
            final DualResponseRegistry dualResponseRegistry,
            final AsyncResultStorePort asyncResultStorePort
    ) {
        this.dualResponseRegistry = dualResponseRegistry;
        this.asyncResultStorePort = asyncResultStorePort;
    }

    /**
     * 수신된 tc.ui.commands 메시지를 eventType에 따라 라우팅합니다.
     *
     * @param reply 수신된 Kafka reply 메시지
     */
    @Override
    public void handle(final KafkaUiTaskReplyMessage reply) {
        final String eventType = reply.metadata().eventType();
        final String traceId   = reply.metadata().traceId();
        final String source    = reply.metadata().source();

        // 수신 로그는 UiCommandKafkaSubscriber(INFO 레벨)에서 이미 출력됩니다.
        // 서비스 계층에서는 라우팅 실패(default 분기) 시에만 WARN을 기록합니다.

        switch (eventType) {
            case EVENT_EQP_CREATE, EVENT_EQP_UPDATE, EVENT_EQP_DELETE ->
                handleDualResponse(traceId, source, reply);

            case EVENT_EQP_START, EVENT_EQP_END ->
                handleAsyncResult(traceId, reply);

            default -> log.warn("처리되지 않은 eventType. eventType={}, traceId={}", eventType, traceId);
        }
    }

    /**
     * eqp_create / eqp_update / eqp_delete 응답을 DualResponseRegistry에 기록합니다.
     *
     * <p>Gateway(TC-COMM-GATEWAY)와 Business Core(TC-BUSINESS-CORE) 양쪽 응답을
     * traceId + source 조합으로 매핑합니다. 양쪽 모두 수신 완료 시 Registry가
     * 자동으로 CompletableFuture를 완료시킵니다.</p>
     *
     * @param traceId 작업 추적 ID
     * @param source  응답 출처 (TC-COMM-GATEWAY 또는 TC-BUSINESS-CORE)
     * @param reply   수신된 reply 메시지
     */
    private void handleDualResponse(
            final String traceId,
            final String source,
            final KafkaUiTaskReplyMessage reply
    ) {
        final UiTaskResult result = toUiTaskResult(traceId, source, reply);
        log.info("DualResponse 기록. traceId={}, source={}, status={}",
                traceId, source, result.status());
        dualResponseRegistry.record(traceId, source, result);
    }

    /**
     * eqp_start / eqp_end 응답을 Redis에 저장합니다.
     *
     * <p>front가 GET /api/async/{traceId} polling으로 결과를 확인합니다.
     * Gateway 단일 응답이므로 DualResponseRegistry를 거치지 않습니다.</p>
     *
     * @param traceId 작업 추적 ID
     * @param reply   수신된 reply 메시지
     */
    private void handleAsyncResult(final String traceId, final KafkaUiTaskReplyMessage reply) {
        log.info("비동기 결과 Redis 저장. traceId={}, status={}", traceId, reply.data().STATUS());
        asyncResultStorePort.save(traceId, reply);
    }

    /**
     * KafkaUiTaskReplyMessage를 UiTaskResult 도메인 객체로 변환합니다.
     *
     * <p>KafkaUiTaskReplyData의 STATUS 문자열을 UiTaskStatus enum으로 매핑합니다.
     * 알 수 없는 STATUS 값은 FAIL로 처리하고 경고 로그를 남깁니다.</p>
     *
     * @param traceId 작업 추적 ID
     * @param source  응답 출처
     * @param reply   변환할 reply 메시지
     * @return 변환된 UiTaskResult
     */
    private UiTaskResult toUiTaskResult(
            final String traceId,
            final String source,
            final KafkaUiTaskReplyMessage reply
    ) {
        final String statusStr = reply.data().STATUS();
        final UiTaskStatus taskStatus = parseStatus(traceId, source, statusStr);

        if (taskStatus == UiTaskStatus.PASS) {
            return UiTaskResult.pass(traceId, source);
        }
        return UiTaskResult.fail(traceId, source, reply.data().ERRORCODE(), reply.data().ERRORMSG());
    }

    /**
     * STATUS 문자열을 UiTaskStatus enum으로 변환합니다.
     *
     * @param traceId   로그용 traceId
     * @param source    로그용 source
     * @param statusStr KafkaUiTaskReplyData.STATUS 값 (PASS/FAIL)
     * @return 변환된 UiTaskStatus (변환 불가 시 FAIL 반환)
     */
    private UiTaskStatus parseStatus(
            final String traceId,
            final String source,
            final String statusStr
    ) {
        try {
            final KafkaUiTaskReplyStatus replyStatus = KafkaUiTaskReplyStatus.valueOf(statusStr);
            return replyStatus == KafkaUiTaskReplyStatus.PASS ? UiTaskStatus.PASS : UiTaskStatus.FAIL;
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 STATUS 값 - FAIL로 처리. traceId={}, source={}, status={}",
                    traceId, source, statusStr);
            return UiTaskStatus.FAIL;
        }
    }
}
