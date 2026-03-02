package com.nori.tc.ui.adapters.web.controller;

import com.nori.tc.messaging.domain.kafka.TcKafkaSources;
import com.nori.tc.messaging.kafka.contract.GatewayEquipmentProfileSnapshot;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import com.nori.tc.ui.adapters.web.config.UiDualRequestProperties;
import com.nori.tc.ui.adapters.web.dto.request.EqpCreateRequest;
import com.nori.tc.ui.adapters.web.dto.request.EqpDeleteRequest;
import com.nori.tc.ui.adapters.web.dto.request.EqpLifecycleRequest;
import com.nori.tc.ui.adapters.web.dto.request.EqpUpdateRequest;
import com.nori.tc.ui.adapters.web.dto.response.ApiResponse;
import com.nori.tc.ui.adapters.web.dto.response.AsyncAcceptResponse;
import com.nori.tc.ui.core.port.messaging.UiBusinessEventPublishPort;
import com.nori.tc.ui.core.port.messaging.UiGatewayEventPublishPort;
import com.nori.tc.ui.core.registry.DualResponseRegistry;
import com.nori.tc.ui.core.registry.UiDualTaskFinalResult;
import com.nori.tc.ui.domain.task.UiTaskResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * 설비(Equipment) 관리 REST API 컨트롤러입니다.
 *
 * <p>제공 엔드포인트:</p>
 * <ul>
 *   <li>POST   /api/eqp          — 설비 등록 (EQP_CREATE), Gateway + Business 동시 발행 후 양방향 응답 대기</li>
 *   <li>PUT    /api/eqp/{eqpId}  — 설비 수정 (EQP_UPDATE), Gateway + Business 동시 발행 후 양방향 응답 대기</li>
 *   <li>DELETE /api/eqp/{eqpId}  — 설비 삭제 (EQP_DELETE), Gateway + Business 동시 발행 후 양방향 응답 대기</li>
 *   <li>POST   /api/eqp/{eqpId}/start — 설비 시작 (EQP_START), Gateway 단독 발행, 202 즉시 반환</li>
 *   <li>POST   /api/eqp/{eqpId}/end   — 설비 종료 (EQP_END),   Gateway 단독 발행, 202 즉시 반환</li>
 * </ul>
 *
 * <p>DualResponse 처리 흐름 (CREATE / UPDATE / DELETE):</p>
 * <ol>
 *   <li>UUID 기반 traceId 생성</li>
 *   <li>Kafka 발행 이전에 {@link DualResponseRegistry}에 traceId 등록 (응답 유실 방지)</li>
 *   <li>Gateway 토픽({@code tc.ui.events.gateway})과 Business 토픽({@code tc.ui.events.business})에 동시 발행</li>
 *   <li>tc.ui.commands 에서 양쪽 응답 수신 후 {@code DualResponseRegistry}가 {@code CompletableFuture} 완료</li>
 *   <li>DeferredResult에 결과 반환 (성공 200 / 실패 500 / 타임아웃 504)</li>
 * </ol>
 *
 * <p>Async 처리 흐름 (START / END):</p>
 * <ol>
 *   <li>UUID 기반 traceId 생성</li>
 *   <li>Gateway 토픽에만 발행 (Business는 미발행)</li>
 *   <li>202 Accepted + traceId 즉시 반환</li>
 *   <li>클라이언트는 {@code GET /api/async/{traceId}}로 결과를 polling</li>
 * </ol>
 *
 * <p>NOTE: traceId는 현재 UUID 기반입니다.
 * 향후 정렬 가능하고 시간 정보가 내장된 ULID로 교체를 검토할 수 있습니다.</p>
 */
@RestController
@RequestMapping("/api/eqp")
@EnableConfigurationProperties(UiDualRequestProperties.class)
public class EqpController {

    private static final Logger log = LoggerFactory.getLogger(EqpController.class);

    private final DualResponseRegistry dualResponseRegistry;
    private final UiGatewayEventPublishPort gatewayEventPublishPort;
    private final UiBusinessEventPublishPort businessEventPublishPort;
    private final UiDualRequestProperties dualRequestProperties;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param dualResponseRegistry      양방향 응답 수집 레지스트리
     * @param gatewayEventPublishPort   Gateway Kafka 이벤트 발행 포트
     * @param businessEventPublishPort  Business Kafka 이벤트 발행 포트
     * @param dualRequestProperties     DualResponse 타임아웃 설정
     */
    public EqpController(
            final DualResponseRegistry dualResponseRegistry,
            final UiGatewayEventPublishPort gatewayEventPublishPort,
            final UiBusinessEventPublishPort businessEventPublishPort,
            final UiDualRequestProperties dualRequestProperties
    ) {
        this.dualResponseRegistry = Objects.requireNonNull(dualResponseRegistry, "dualResponseRegistry is null");
        this.gatewayEventPublishPort = Objects.requireNonNull(gatewayEventPublishPort, "gatewayEventPublishPort is null");
        this.businessEventPublishPort = Objects.requireNonNull(businessEventPublishPort, "businessEventPublishPort is null");
        this.dualRequestProperties = Objects.requireNonNull(dualRequestProperties, "dualRequestProperties is null");
    }

    // -------------------------------------------------------------------------
    // DualResponse 엔드포인트: EQP_CREATE / EQP_UPDATE / EQP_DELETE
    // -------------------------------------------------------------------------

    /**
     * 설비를 Gateway와 Business Core에 등록합니다.
     *
     * <p>Gateway + Business 양쪽의 응답을 모두 수신한 후 최종 결과를 반환합니다.
     * {@code equipmentProfile}을 포함하면 Gateway가 DB 재조회 없이 EQP bean을 초기화합니다.</p>
     *
     * @param request 설비 등록 요청 (eqpId, interfaceType 필수)
     * @return 200 OK (양쪽 모두 성공) | 500 오류 | 504 타임아웃
     */
    @PostMapping
    public DeferredResult<ResponseEntity<ApiResponse<Void>>> create(
            @Valid @RequestBody final EqpCreateRequest request
    ) {
        final String traceId = generateTraceId();
        final String eqpId = request.eqpId();

        log.info("EQP_CREATE 요청. eqpId={}, interfaceType={}, traceId={}",
                eqpId, request.interfaceType(), traceId);

        final KafkaUiTaskMessage message = buildMessage(
                KafkaUiTaskEventType.EQP_CREATE, traceId, eqpId,
                request.interfaceType(), request.uiMessage(), request.equipmentProfile()
        );

        return submitDualRequest(KafkaUiTaskEventType.EQP_CREATE, traceId, eqpId, message);
    }

    /**
     * 설비 정보를 Gateway와 Business Core에서 수정합니다.
     *
     * <p>eqpId는 경로 변수로 전달됩니다. {@code equipmentProfile}에 변경된
     * 설비 구성 스냅샷을 포함하면 Gateway가 DB 재조회 없이 갱신합니다.</p>
     *
     * @param eqpId   수정 대상 설비 ID (경로 변수)
     * @param request 설비 수정 요청 (interfaceType 필수)
     * @return 200 OK (양쪽 모두 성공) | 500 오류 | 504 타임아웃
     */
    @PutMapping("/{eqpId}")
    public DeferredResult<ResponseEntity<ApiResponse<Void>>> update(
            @PathVariable final String eqpId,
            @Valid @RequestBody final EqpUpdateRequest request
    ) {
        final String traceId = generateTraceId();

        log.info("EQP_UPDATE 요청. eqpId={}, interfaceType={}, traceId={}",
                eqpId, request.interfaceType(), traceId);

        final KafkaUiTaskMessage message = buildMessage(
                KafkaUiTaskEventType.EQP_UPDATE, traceId, eqpId,
                request.interfaceType(), request.uiMessage(), request.equipmentProfile()
        );

        return submitDualRequest(KafkaUiTaskEventType.EQP_UPDATE, traceId, eqpId, message);
    }

    /**
     * 설비를 Gateway와 Business Core에서 삭제합니다.
     *
     * <p>eqpId는 경로 변수로 전달됩니다. 삭제 요청은 equipmentProfile이 불필요합니다.</p>
     *
     * @param eqpId   삭제 대상 설비 ID (경로 변수)
     * @param request 설비 삭제 요청 (interfaceType 필수)
     * @return 200 OK (양쪽 모두 성공) | 500 오류 | 504 타임아웃
     */
    @DeleteMapping("/{eqpId}")
    public DeferredResult<ResponseEntity<ApiResponse<Void>>> delete(
            @PathVariable final String eqpId,
            @Valid @RequestBody final EqpDeleteRequest request
    ) {
        final String traceId = generateTraceId();

        log.info("EQP_DELETE 요청. eqpId={}, interfaceType={}, traceId={}",
                eqpId, request.interfaceType(), traceId);

        final KafkaUiTaskMessage message = buildMessage(
                KafkaUiTaskEventType.EQP_DELETE, traceId, eqpId,
                request.interfaceType(), request.uiMessage(), null
        );

        return submitDualRequest(KafkaUiTaskEventType.EQP_DELETE, traceId, eqpId, message);
    }

    // -------------------------------------------------------------------------
    // Async 엔드포인트: EQP_START / EQP_END (202 즉시 반환)
    // -------------------------------------------------------------------------

    /**
     * 설비 시작 명령을 Gateway에 전송합니다.
     *
     * <p>Gateway 단독으로 처리하므로 Business 토픽에 발행하지 않습니다.
     * 202 Accepted + traceId를 즉시 반환하고, 결과는 {@code GET /api/async/{traceId}}로 polling합니다.
     * Gateway 응답은 tc.ui.commands 토픽 수신 후 Redis에 저장됩니다.</p>
     *
     * @param eqpId   시작 대상 설비 ID (경로 변수)
     * @param request 설비 시작 요청 (interfaceType 필수)
     * @return 202 Accepted + traceId | 500 발행 실패
     */
    @PostMapping("/{eqpId}/start")
    public ResponseEntity<ApiResponse<AsyncAcceptResponse>> start(
            @PathVariable final String eqpId,
            @Valid @RequestBody final EqpLifecycleRequest request
    ) {
        final String traceId = generateTraceId();

        log.info("EQP_START 요청. eqpId={}, interfaceType={}, traceId={}",
                eqpId, request.interfaceType(), traceId);

        final KafkaUiTaskMessage message = buildMessage(
                KafkaUiTaskEventType.EQP_START, traceId, eqpId,
                request.interfaceType(), request.uiMessage(), null
        );

        return publishLifecycleAndAccept(KafkaUiTaskEventType.EQP_START, traceId, eqpId, message);
    }

    /**
     * 설비 종료 명령을 Gateway에 전송합니다.
     *
     * <p>Gateway 단독으로 처리하므로 Business 토픽에 발행하지 않습니다.
     * 202 Accepted + traceId를 즉시 반환하고, 결과는 {@code GET /api/async/{traceId}}로 polling합니다.
     * Gateway 응답은 tc.ui.commands 토픽 수신 후 Redis에 저장됩니다.</p>
     *
     * @param eqpId   종료 대상 설비 ID (경로 변수)
     * @param request 설비 종료 요청 (interfaceType 필수)
     * @return 202 Accepted + traceId | 500 발행 실패
     */
    @PostMapping("/{eqpId}/end")
    public ResponseEntity<ApiResponse<AsyncAcceptResponse>> end(
            @PathVariable final String eqpId,
            @Valid @RequestBody final EqpLifecycleRequest request
    ) {
        final String traceId = generateTraceId();

        log.info("EQP_END 요청. eqpId={}, interfaceType={}, traceId={}",
                eqpId, request.interfaceType(), traceId);

        final KafkaUiTaskMessage message = buildMessage(
                KafkaUiTaskEventType.EQP_END, traceId, eqpId,
                request.interfaceType(), request.uiMessage(), null
        );

        return publishLifecycleAndAccept(KafkaUiTaskEventType.EQP_END, traceId, eqpId, message);
    }

    // -------------------------------------------------------------------------
    // 내부 유틸: DualRequest 공통 처리
    // -------------------------------------------------------------------------

    /**
     * DualResponse 패턴으로 Kafka 발행 후 양방향 응답을 비동기 대기합니다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>{@link DualResponseRegistry#register}를 Kafka 발행 이전에 호출하여 응답 유실 방지</li>
     *   <li>CompletableFuture 완료 시 DeferredResult에 결과 설정</li>
     *   <li>Gateway + Business 토픽에 동시 발행</li>
     *   <li>발행 실패 시 future 취소 → whenComplete에서 PUBLISH_FAILED 처리</li>
     * </ol>
     *
     * @param eventType  이벤트 타입 (로그용)
     * @param traceId    작업 추적 ID
     * @param eqpId      설비 ID (로그용)
     * @param message    발행할 Kafka 메시지 (gateway/business 동일 내용)
     * @return DeferredResult (성공 200 / 실패 500 / 타임아웃 504)
     */
    private DeferredResult<ResponseEntity<ApiResponse<Void>>> submitDualRequest(
            final KafkaUiTaskEventType eventType,
            final String traceId,
            final String eqpId,
            final KafkaUiTaskMessage message
    ) {
        final DeferredResult<ResponseEntity<ApiResponse<Void>>> deferredResult = new DeferredResult<>();

        // 1단계: Kafka 발행 이전에 등록 (응답이 발행보다 먼저 도착하는 엣지 케이스 방어)
        final CompletableFuture<UiDualTaskFinalResult> future =
                dualResponseRegistry.register(traceId, dualRequestProperties.getDualRequestTimeoutMs());

        // 2단계: Future 완료 시 DeferredResult에 결과 설정 (별도 스레드에서 실행될 수 있음)
        future.whenComplete((result, throwable) ->
                resolveDeferredResult(deferredResult, eventType, traceId, eqpId, result, throwable)
        );

        // 3단계: Gateway + Business 토픽에 동시 발행
        try {
            gatewayEventPublishPort.publish(message);
            businessEventPublishPort.publish(message);
            log.debug("DualRequest Kafka 발행 완료. eventType={}, traceId={}, eqpId={}",
                    eventType, traceId, eqpId);
        } catch (Exception e) {
            // 발행 실패: Future 취소 → whenComplete에서 CancellationException으로 처리
            log.error("DualRequest Kafka 발행 실패. eventType={}, traceId={}, eqpId={}",
                    eventType, traceId, eqpId, e);
            future.cancel(true);
        }

        return deferredResult;
    }

    /**
     * DualResponse Future 완료 결과를 DeferredResult로 변환합니다.
     *
     * <p>판정 규칙:</p>
     * <ul>
     *   <li>CancellationException → Kafka 발행 실패 → 500 PUBLISH_FAILED</li>
     *   <li>TimeoutException → 타임아웃 초과 → 504 TIMEOUT</li>
     *   <li>기타 예외 → 내부 오류 → 500 INTERNAL_ERROR</li>
     *   <li>result.success() → 양쪽 PASS → 200 OK</li>
     *   <li>result.success()=false → 한쪽 이상 FAIL → 500 (첫 번째 실패 코드/메시지 반환)</li>
     * </ul>
     *
     * @param deferredResult 결과를 설정할 DeferredResult
     * @param eventType      이벤트 타입 (로그용)
     * @param traceId        작업 추적 ID (로그용)
     * @param eqpId          설비 ID (로그용)
     * @param result         Future 완료 결과 (성공 시)
     * @param throwable      Future 완료 예외 (실패 시)
     */
    private void resolveDeferredResult(
            final DeferredResult<ResponseEntity<ApiResponse<Void>>> deferredResult,
            final KafkaUiTaskEventType eventType,
            final String traceId,
            final String eqpId,
            final UiDualTaskFinalResult result,
            final Throwable throwable
    ) {
        if (throwable instanceof CancellationException) {
            // Kafka 발행 실패로 future.cancel(true) 호출 시 발생
            log.warn("DualResponse 강제 취소 (발행 실패). eventType={}, traceId={}, eqpId={}",
                    eventType, traceId, eqpId);
            deferredResult.setResult(ResponseEntity.internalServerError()
                    .body(ApiResponse.error("PUBLISH_FAILED", "Kafka 발행 중 오류가 발생했습니다.")));

        } else if (throwable instanceof TimeoutException) {
            // DualResponseRegistry.register()의 orTimeout() 초과
            log.warn("DualResponse 타임아웃. eventType={}, traceId={}, eqpId={}, timeoutMs={}",
                    eventType, traceId, eqpId, dualRequestProperties.getDualRequestTimeoutMs());
            deferredResult.setResult(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(ApiResponse.error("TIMEOUT", "Gateway/Business 응답 대기 시간이 초과되었습니다.")));

        } else if (throwable != null) {
            // 예상치 못한 예외 (Redis 장애, 레지스트리 내부 오류 등)
            log.error("DualResponse 비정상 종료. eventType={}, traceId={}, eqpId={}",
                    eventType, traceId, eqpId, throwable);
            deferredResult.setResult(ResponseEntity.internalServerError()
                    .body(ApiResponse.error("INTERNAL_ERROR", "처리 중 내부 오류가 발생했습니다.")));

        } else if (result.success()) {
            // Gateway + Business 모두 PASS
            log.info("DualResponse 완료 (성공). eventType={}, traceId={}, eqpId={}",
                    eventType, traceId, eqpId);
            deferredResult.setResult(ResponseEntity.ok(ApiResponse.success(null)));

        } else {
            // 한쪽 또는 양쪽 FAIL — firstFailedResult()로 우선 Gateway 실패를 반환
            final UiTaskResult failed = result.firstFailedResult().orElse(null);
            final String errorCode = failed != null ? failed.errorCode() : "PROCESSING_FAILED";
            final String errorMsg = failed != null ? failed.errorMsg() : "처리에 실패했습니다.";

            log.warn("DualResponse 완료 (실패). eventType={}, traceId={}, eqpId={}, "
                            + "partialFailure={}, errorCode={}, errorMsg={}",
                    eventType, traceId, eqpId, result.hasPartialFailure(), errorCode, errorMsg);

            deferredResult.setResult(ResponseEntity.internalServerError()
                    .body(ApiResponse.error(errorCode, errorMsg)));
        }
    }

    // -------------------------------------------------------------------------
    // 내부 유틸: Lifecycle(START/END) 공통 처리
    // -------------------------------------------------------------------------

    /**
     * Lifecycle 이벤트(EQP_START / EQP_END)를 Gateway에 발행하고 202를 반환합니다.
     *
     * <p>Business 토픽에는 발행하지 않습니다.
     * 결과는 tc.ui.commands 수신 후 Redis에 저장되어 클라이언트가 polling으로 확인합니다.</p>
     *
     * @param eventType 이벤트 타입 (EQP_START 또는 EQP_END)
     * @param traceId   작업 추적 ID
     * @param eqpId     설비 ID (로그용)
     * @param message   발행할 Kafka 메시지
     * @return 202 Accepted + traceId | 500 발행 실패
     */
    private ResponseEntity<ApiResponse<AsyncAcceptResponse>> publishLifecycleAndAccept(
            final KafkaUiTaskEventType eventType,
            final String traceId,
            final String eqpId,
            final KafkaUiTaskMessage message
    ) {
        try {
            gatewayEventPublishPort.publish(message);
            log.info("{} Kafka 발행 완료. eqpId={}, traceId={}", eventType, eqpId, traceId);
        } catch (Exception e) {
            log.error("{} Kafka 발행 실패. eqpId={}, traceId={}", eventType, eqpId, traceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("PUBLISH_FAILED", "Kafka 발행 중 오류가 발생했습니다."));
        }

        return ResponseEntity.accepted()
                .body(ApiResponse.success(new AsyncAcceptResponse(traceId)));
    }

    // -------------------------------------------------------------------------
    // 내부 유틸: 메시지 빌드 / traceId 생성
    // -------------------------------------------------------------------------

    /**
     * KafkaUiTaskMessage를 생성합니다.
     *
     * <p>metadata: eventType + 현재 시각 + UI_BACKEND source + traceId</p>
     * <p>data: eqpId + interfaceType + uiMessage + equipmentProfile</p>
     *
     * @param eventType        이벤트 타입
     * @param traceId          작업 추적 ID
     * @param eqpId            설비 ID
     * @param interfaceType    인터페이스 타입
     * @param uiMessage        UI 메시지 (null 허용)
     * @param equipmentProfile 설비 프로파일 스냅샷 (null 허용 — DELETE/START/END 시 생략)
     * @return 생성된 KafkaUiTaskMessage
     */
    private KafkaUiTaskMessage buildMessage(
            final KafkaUiTaskEventType eventType,
            final String traceId,
            final String eqpId,
            final String interfaceType,
            final String uiMessage,
            final GatewayEquipmentProfileSnapshot equipmentProfile
    ) {
        return new KafkaUiTaskMessage(
                new KafkaUiTaskMessage.KafkaUiTaskMetadata(
                        eventType.name(),
                        OffsetDateTime.now().toString(),
                        TcKafkaSources.UI_BACKEND,
                        traceId
                ),
                new KafkaUiTaskMessage.KafkaUiTaskData(eqpId, interfaceType, uiMessage, equipmentProfile)
        );
    }

    /**
     * 요청별 고유 작업 추적 ID를 생성합니다.
     *
     * <p>현재 UUID v4 기반입니다.
     * TODO: 향후 시간 정렬 가능하고 단조 증가를 보장하는 ULID로 교체를 검토하십시오.
     * (ULID는 정렬 기반 traceId 조회, 분산 환경의 디버깅 편의성을 높입니다.)</p>
     *
     * @return 무작위 UUID 문자열
     */
    private static String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}
