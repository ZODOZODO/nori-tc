package com.nori.tc.ui.adapters.web.controller;

import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyMessage;
import com.nori.tc.ui.adapters.web.dto.response.ApiResponse;
import com.nori.tc.ui.adapters.web.dto.response.AsyncResultResponse;
import com.nori.tc.ui.core.port.redis.AsyncResultStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Optional;

/**
 * 비동기 작업 결과 polling REST API 컨트롤러입니다.
 *
 * <p>제공 엔드포인트:</p>
 * <ul>
 *   <li>GET /api/async/{traceId} — eqp_start / eqp_end 작업 결과 polling</li>
 * </ul>
 *
 * <p>polling 흐름:</p>
 * <ol>
 *   <li>클라이언트가 EQP_START 또는 EQP_END 요청 → 202 Accepted + traceId 수신</li>
 *   <li>Gateway가 처리 결과를 tc.ui.commands 토픽에 발행</li>
 *   <li>UiCommandKafkaSubscriber가 수신하여 {@link AsyncResultStorePort}에 Redis 저장</li>
 *   <li>클라이언트가 이 엔드포인트로 traceId를 사용해 polling → 결과가 있으면 200, 없으면 404</li>
 * </ol>
 *
 * <p>응답 규칙:</p>
 * <ul>
 *   <li>결과 있음 → 200 OK + {@link AsyncResultResponse} (status=PASS 또는 FAIL)</li>
 *   <li>결과 없음 (처리 중 또는 TTL 만료) → 404 Not Found</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/async")
public class AsyncResultController {

    private static final Logger log = LoggerFactory.getLogger(AsyncResultController.class);

    private final AsyncResultStorePort asyncResultStorePort;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param asyncResultStorePort 비동기 작업 결과 조회 포트 (Business Redis)
     */
    public AsyncResultController(final AsyncResultStorePort asyncResultStorePort) {
        this.asyncResultStorePort = Objects.requireNonNull(asyncResultStorePort,
                "asyncResultStorePort is null");
    }

    /**
     * traceId로 비동기 작업 결과를 조회합니다.
     *
     * <p>eqp_start / eqp_end 요청 후 Gateway 응답이 Business Redis에 저장되면
     * 200 OK로 반환됩니다. 아직 처리 중이거나 TTL이 만료된 경우 404를 반환합니다.</p>
     *
     * <p>클라이언트 polling 권장 간격:</p>
     * <p>Gateway 처리 시간은 설비 상태에 따라 수백 밀리초 ~ 수십 초까지 다를 수 있습니다.
     * 초기 1초 간격으로 시작하여 결과가 없으면 지수 백오프 방식으로 간격을 늘리는 것을 권장합니다.</p>
     *
     * @param traceId 조회할 작업 추적 ID (EQP_START/END 요청 시 반환된 traceId)
     * @return 200 OK + 작업 결과 | 404 (아직 처리 중 또는 결과 만료)
     */
    @GetMapping("/{traceId}")
    public ResponseEntity<ApiResponse<AsyncResultResponse>> getResult(
            @PathVariable final String traceId
    ) {
        log.trace("비동기 결과 조회 요청. traceId={}", traceId);

        final Optional<KafkaUiTaskReplyMessage> replyOptional = asyncResultStorePort.get(traceId);

        if (replyOptional.isEmpty()) {
            // 아직 처리 중이거나 TTL이 만료된 경우
            log.debug("비동기 결과 없음 (처리 중 또는 만료). traceId={}", traceId);
            return ResponseEntity.notFound().build();
        }

        final KafkaUiTaskReplyMessage reply = replyOptional.get();
        final KafkaUiTaskReplyMessage.KafkaUiTaskReplyData data = reply.data();

        log.debug("비동기 결과 조회 완료. traceId={}, eqpId={}, status={}",
                traceId, data.eqpId(), data.STATUS());

        final AsyncResultResponse response = new AsyncResultResponse(
                traceId,
                data.eqpId(),
                data.STATUS(),
                data.ERRORCODE(),
                data.ERRORMSG()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
