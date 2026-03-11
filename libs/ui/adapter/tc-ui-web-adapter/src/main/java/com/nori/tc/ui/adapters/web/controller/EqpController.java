package com.nori.tc.ui.adapters.web.controller;

import com.nori.tc.comm.gateway.domain.profile.GatewayEquipmentProfileSnapshot;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.messaging.domain.kafka.TcKafkaSources;
import com.nori.tc.ui.adapters.web.config.UiDualRequestProperties;
import com.nori.tc.ui.adapters.web.controller.support.UiPageRequestSupport;
import com.nori.tc.ui.adapters.web.dto.request.EqpCreateRequest;
import com.nori.tc.ui.adapters.web.dto.request.EqpLifecycleRequest;
import com.nori.tc.ui.adapters.web.dto.request.EqpUpdateRequest;
import com.nori.tc.ui.adapters.web.dto.response.ApiResponse;
import com.nori.tc.ui.adapters.web.dto.response.AsyncAcceptResponse;
import com.nori.tc.ui.adapters.web.dto.response.EqpInfoResponse;
import com.nori.tc.ui.adapters.web.dto.response.EqpRuntimeStateResponse;
import com.nori.tc.ui.core.model.PagedResponse;
import com.nori.tc.ui.core.model.UiCommandEventType;
import com.nori.tc.ui.core.model.UiCommandMessage;
import com.nori.tc.ui.core.port.db.EqpQueryPort;
import com.nori.tc.ui.core.port.messaging.UiBusinessEventPublishPort;
import com.nori.tc.ui.core.port.messaging.UiGatewayEventPublishPort;
import com.nori.tc.ui.core.port.redis.AsyncResultStorePort;
import com.nori.tc.ui.core.registry.DualResponseRegistry;
import com.nori.tc.ui.core.registry.UiDualTaskFinalResult;
import com.nori.tc.ui.domain.task.UiTaskResult;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * 설비(Equipment) 관리 REST API 컨트롤러입니다.
 *
 * <p>제공 엔드포인트:</p>
 * <ul>
 *   <li>GET    /api/eqp          — 설비 목록 조회 (DB 기반)</li>
 *   <li>GET    /api/eqp/{eqpId}  — 설비 상세 조회 (DB 기반)</li>
 *   <li>GET    /api/eqp/{eqpId}/param-versions — 설비 파라미터 버전 목록 조회 (tc_eqp_param 기반)</li>
 *   <li>GET    /api/eqp/{eqpId}/runtime-state  — 설비 런타임 상태 조회 (tc_eqp_state/tc_eqp_state_hist 기반)</li>
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
    private static final String ROLLBACK_UI_MESSAGE_PREFIX = "ROLLBACK";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final EqpQueryPort eqpQueryPort;
    private final DualResponseRegistry dualResponseRegistry;
    private final UiGatewayEventPublishPort gatewayEventPublishPort;
    private final UiBusinessEventPublishPort businessEventPublishPort;
    private final AsyncResultStorePort asyncResultStorePort;
    private final UiDualRequestProperties dualRequestProperties;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param eqpQueryPort              설비 조회 포트
     * @param dualResponseRegistry      양방향 응답 수집 레지스트리
     * @param gatewayEventPublishPort   Gateway Kafka 이벤트 발행 포트
     * @param businessEventPublishPort  Business Kafka 이벤트 발행 포트
     * @param asyncResultStorePort      START/END polling 상태 저장 포트
     * @param dualRequestProperties     DualResponse 타임아웃 설정
     */
    public EqpController(
            final EqpQueryPort eqpQueryPort,
            final DualResponseRegistry dualResponseRegistry,
            final UiGatewayEventPublishPort gatewayEventPublishPort,
            final UiBusinessEventPublishPort businessEventPublishPort,
            final AsyncResultStorePort asyncResultStorePort,
            final UiDualRequestProperties dualRequestProperties
    ) {
        this.eqpQueryPort = Objects.requireNonNull(eqpQueryPort, "eqpQueryPort is null");
        this.dualResponseRegistry = Objects.requireNonNull(dualResponseRegistry, "dualResponseRegistry is null");
        this.gatewayEventPublishPort = Objects.requireNonNull(gatewayEventPublishPort, "gatewayEventPublishPort is null");
        this.businessEventPublishPort = Objects.requireNonNull(businessEventPublishPort, "businessEventPublishPort is null");
        this.asyncResultStorePort = Objects.requireNonNull(asyncResultStorePort, "asyncResultStorePort is null");
        this.dualRequestProperties = Objects.requireNonNull(dualRequestProperties, "dualRequestProperties is null");
    }

    // -------------------------------------------------------------------------
    // 조회 엔드포인트: GET /api/eqp, GET /api/eqp/{eqpId}
    // -------------------------------------------------------------------------

    /**
     * 설비 목록을 페이지 단위로 조회합니다.
     *
     * @param offset 조회 시작 위치(기본값 0)
     * @param limit  조회 건수(기본값 100, 최대 500)
     * @return 목록 페이지 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<EqpInfoResponse>>> list(
            @RequestParam(name = "offset", required = false) final Integer offset,
            @RequestParam(name = "limit", required = false) final Integer limit
    ) {
        final PageRequest pageRequest = UiPageRequestSupport.resolve(offset, limit);

        if (log.isDebugEnabled()) {
            log.debug("설비 목록 조회 요청. offset={}, limit={}", pageRequest.offset(), pageRequest.limit());
        }

        final PagedResponse<TcEqp> page = eqpQueryPort.findAll(pageRequest);
        final PagedResponse<EqpInfoResponse> responsePage = toEqpPage(page);

        if (log.isDebugEnabled()) {
            log.debug("설비 목록 조회 완료. offset={}, limit={}, pageSize={}, totalCount={}",
                    responsePage.offset(), responsePage.limit(), responsePage.items().size(), responsePage.count());
        }

        return ResponseEntity.ok(ApiResponse.success(responsePage));
    }

    /**
     * 설비 ID(eqpId) 기준으로 단건을 조회합니다.
     *
     * @param eqpId 설비 비즈니스 ID
     * @return 단건 조회 응답
     */
    @GetMapping("/{eqpId}")
    public ResponseEntity<ApiResponse<EqpInfoResponse>> get(
            @PathVariable final String eqpId
    ) {
        if (log.isDebugEnabled()) {
            log.debug("설비 단건 조회 요청. eqpId={}", eqpId);
        }

        final Optional<TcEqp> optionalEqp = eqpQueryPort.findByEqpId(eqpId);
        if (optionalEqp.isEmpty()) {
            log.warn("설비 단건 조회 결과 없음. eqpId={}", eqpId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "설비를 찾을 수 없습니다."));
        }

        return ResponseEntity.ok(ApiResponse.success(toEqpInfoResponse(optionalEqp.get())));
    }

    /**
     * 설비 ID(eqpId)에 매핑된 파라미터 버전 목록을 조회합니다.
     *
     * <p>버전 소스는 {@code tc_eqp_param.param_version}이며, 동일 버전은 중복 없이 반환됩니다.</p>
     *
     * @param eqpId 설비 비즈니스 ID
     * @return 버전 목록 응답
     */
    @GetMapping("/{eqpId}/param-versions")
    public ResponseEntity<ApiResponse<List<String>>> getParamVersions(
            @PathVariable final String eqpId
    ) {
        if (log.isDebugEnabled()) {
            log.debug("설비 파라미터 버전 조회 요청. eqpId={}", eqpId);
        }

        final Optional<TcEqp> optionalEqp = eqpQueryPort.findByEqpId(eqpId);
        if (optionalEqp.isEmpty()) {
            log.warn("설비 파라미터 버전 조회 결과 없음 - 설비 미존재. eqpId={}", eqpId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "설비를 찾을 수 없습니다."));
        }

        final List<String> versions = eqpQueryPort.findParamVersionsByEqpId(eqpId);

        if (log.isDebugEnabled()) {
            log.debug("설비 파라미터 버전 조회 완료. eqpId={}, versionCount={}", eqpId, versions.size());
        }

        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    /**
     * 설비 ID(eqpId)에 매핑된 런타임 상태를 조회합니다.
     *
     * <p>상태 소스:</p>
     * <ul>
     *   <li>tc_eqp_state: control_state, eqp_state</li>
     *   <li>tc_eqp_state_hist: 최신 CONN 이력(to_state)</li>
     * </ul>
     *
     * @param eqpId 설비 비즈니스 ID
     * @return 런타임 상태 응답
     */
    @GetMapping("/{eqpId}/runtime-state")
    public ResponseEntity<ApiResponse<EqpRuntimeStateResponse>> getRuntimeState(
            @PathVariable final String eqpId
    ) {
        if (log.isDebugEnabled()) {
            log.debug("설비 런타임 상태 조회 요청. eqpId={}", eqpId);
        }

        final Optional<EqpQueryPort.EqpRuntimeStateView> optionalState = eqpQueryPort.findRuntimeStateByEqpId(eqpId);
        if (optionalState.isEmpty()) {
            log.warn("설비 런타임 상태 조회 결과 없음 - 설비 미존재. eqpId={}", eqpId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "설비를 찾을 수 없습니다."));
        }

        final EqpQueryPort.EqpRuntimeStateView state = optionalState.get();
        final EqpRuntimeStateResponse response = new EqpRuntimeStateResponse(
                state.controlState(),
                state.eqpState(),
                state.connectionState()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
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

        try (MdcTraceScope ignored = openTraceMdcScope(traceId)) {
            log.info("EQP_CREATE 요청. eqpId={}, interfaceType={}, traceId={}",
                    eqpId, request.interfaceType(), traceId);

            final UiCommandMessage message = buildMessage(
                    UiCommandEventType.EQP_CREATE, traceId, eqpId,
                    request.interfaceType(), request.uiMessage(), request.equipmentProfile()
            );

            return submitDualRequest(UiCommandEventType.EQP_CREATE, traceId, eqpId, message);
        }
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

        try (MdcTraceScope ignored = openTraceMdcScope(traceId)) {
            log.info("EQP_UPDATE 요청. eqpId={}, interfaceType={}, traceId={}",
                    eqpId, request.interfaceType(), traceId);

            final UiCommandMessage message = buildMessage(
                    UiCommandEventType.EQP_UPDATE, traceId, eqpId,
                    request.interfaceType(), request.uiMessage(), request.equipmentProfile()
            );

            return submitDualRequest(UiCommandEventType.EQP_UPDATE, traceId, eqpId, message);
        }
    }

    /**
     * 설비를 Gateway와 Business Core에서 삭제합니다.
     *
     * <p>4.10 API-01 정책에 따라 DELETE 요청 본문 의존을 제거하고,
     * 삭제에 필요한 입력값은 경로/쿼리 파라미터로만 받습니다.</p>
     *
     * <p>호환 정책:</p>
     * <ul>
     *   <li>요청 본문이 오더라도 무시하고 WARN 로그만 남깁니다.</li>
     *   <li>{@code interfaceType} 쿼리 파라미터가 누락되면 400을 명시적으로 반환합니다.</li>
     * </ul>
     *
     * @param eqpId          삭제 대상 설비 ID (경로 변수)
     * @param interfaceType  통신 인터페이스 타입 (쿼리 파라미터, 필수)
     * @param uiMessage      부가 메시지 (쿼리 파라미터, 선택)
     * @param request        원본 HTTP 요청 (호환성 진단 로그용)
     * @return 200 OK (양쪽 모두 성공) | 500 오류 | 504 타임아웃
     */
    @DeleteMapping("/{eqpId}")
    public DeferredResult<ResponseEntity<ApiResponse<Void>>> delete(
            @PathVariable final String eqpId,
            @RequestParam(name = "interfaceType", required = false) final String interfaceType,
            @RequestParam(name = "uiMessage", required = false) final String uiMessage,
            final HttpServletRequest request
    ) {
        final String traceId = generateTraceId();

        try (MdcTraceScope ignored = openTraceMdcScope(traceId)) {
            if (request != null && request.getContentLengthLong() > 0L) {
                log.warn("EQP_DELETE 요청 본문 감지 - 본문은 무시되고 쿼리 파라미터만 사용됩니다. eqpId={}, traceId={}",
                        eqpId, traceId);
            }

            if (interfaceType == null || interfaceType.isBlank()) {
                log.warn("EQP_DELETE 요청 거부 - interfaceType 쿼리 파라미터 누락. eqpId={}, traceId={}",
                        eqpId, traceId);
                final DeferredResult<ResponseEntity<ApiResponse<Void>>> badRequest = new DeferredResult<>();
                badRequest.setResult(ResponseEntity.badRequest()
                        .body(ApiResponse.error("INVALID_REQUEST", "interfaceType 쿼리 파라미터는 필수입니다.")));
                return badRequest;
            }

            log.info("EQP_DELETE 요청. eqpId={}, interfaceType={}, traceId={}",
                    eqpId, interfaceType, traceId);

            final UiCommandMessage message = buildMessage(
                    UiCommandEventType.EQP_DELETE, traceId, eqpId,
                    interfaceType, uiMessage, null
            );

            return submitDualRequest(UiCommandEventType.EQP_DELETE, traceId, eqpId, message);
        }
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

        try (MdcTraceScope ignored = openTraceMdcScope(traceId)) {
            log.info("EQP_START 요청. eqpId={}, interfaceType={}, traceId={}",
                    eqpId, request.interfaceType(), traceId);

            final UiCommandMessage message = buildMessage(
                    UiCommandEventType.EQP_START, traceId, eqpId,
                    request.interfaceType(), request.uiMessage(), null
            );

            return publishLifecycleAndAccept(UiCommandEventType.EQP_START, traceId, eqpId, message);
        }
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

        try (MdcTraceScope ignored = openTraceMdcScope(traceId)) {
            log.info("EQP_END 요청. eqpId={}, interfaceType={}, traceId={}",
                    eqpId, request.interfaceType(), traceId);

            final UiCommandMessage message = buildMessage(
                    UiCommandEventType.EQP_END, traceId, eqpId,
                    request.interfaceType(), request.uiMessage(), null
            );

            return publishLifecycleAndAccept(UiCommandEventType.EQP_END, traceId, eqpId, message);
        }
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
     *   <li>발행 실패 시 {@link DualResponseRegistry#cancel(String)}로 정리 후
     *       즉시 {@code PUBLISH_FAILED} 응답 반환</li>
     * </ol>
     *
     * @param eventType  이벤트 타입 (로그용)
     * @param traceId    작업 추적 ID
     * @param eqpId      설비 ID (로그용)
     * @param message    발행할 UI 명령 메시지 (gateway/business 동일 내용)
     * @return DeferredResult (성공 200 / 실패 500 / 타임아웃 504)
     */
    private DeferredResult<ResponseEntity<ApiResponse<Void>>> submitDualRequest(
            final UiCommandEventType eventType,
            final String traceId,
            final String eqpId,
            final UiCommandMessage message
    ) {
        final DeferredResult<ResponseEntity<ApiResponse<Void>>> deferredResult = new DeferredResult<>();

        // 1단계: Kafka 발행 이전에 등록 (응답이 발행보다 먼저 도착하는 엣지 케이스 방어)
        final CompletableFuture<UiDualTaskFinalResult> future =
                dualResponseRegistry.register(traceId, dualRequestProperties.getDualRequestTimeoutMs());

        // 2단계: Future 완료 시 DeferredResult에 결과 설정 (별도 스레드에서 실행될 수 있음)
        future.whenComplete((result, throwable) -> {
            try (MdcTraceScope ignored = openTraceMdcScope(traceId)) {
                resolveDeferredResult(deferredResult, eventType, traceId, eqpId, result, throwable);
            }
        });

        // 3단계: Gateway + Business 토픽에 동시 발행
        boolean gatewayPublished = false;
        try {
            gatewayEventPublishPort.publish(message);
            gatewayPublished = true;
            businessEventPublishPort.publish(message);
            log.debug("DualRequest Kafka 발행 완료. eventType={}, traceId={}, eqpId={}",
                    eventType, traceId, eqpId);
        } catch (Exception e) {
            log.error("DualRequest Kafka 발행 실패. eventType={}, traceId={}, eqpId={}",
                    eventType, traceId, eqpId, e);
            if (gatewayPublished) {
                publishRollbackIfNeeded(eventType, traceId, eqpId, message);
            }
            dualResponseRegistry.cancel(traceId);
            setPublishFailedResultIfPossible(deferredResult, eventType, traceId, eqpId);
        }

        return deferredResult;
    }

    /**
     * Business 발행 실패 시 Gateway 보상 이벤트를 발행합니다.
     *
     * <p>현재는 EQP_CREATE 케이스만 자동 보상이 가능합니다. UPDATE/DELETE는 이전 상태 스냅샷이 없어
     * 안전한 자동 복구가 불가능하므로 오류 로그를 남기고 수동 조치를 유도합니다.</p>
     *
     * @param failedEventType 실패한 원본 이벤트 타입
     * @param failedTraceId 실패한 원본 traceId
     * @param eqpId 설비 ID
     * @param originalMessage 원본 발행 메시지
     */
    private void publishRollbackIfNeeded(
            final UiCommandEventType failedEventType,
            final String failedTraceId,
            final String eqpId,
            final UiCommandMessage originalMessage
    ) {
        if (failedEventType != UiCommandEventType.EQP_CREATE) {
            log.error(
                    "Business 발행 실패 보상 자동화 미지원 이벤트. failedEventType={}, traceId={}, eqpId={}, 수동 정합성 점검 필요",
                    failedEventType, failedTraceId, eqpId
            );
            return;
        }

        final UiCommandMessage rollbackMessage = buildMessage(
                UiCommandEventType.EQP_DELETE,
                generateTraceId(),
                eqpId,
                originalMessage.interfaceType(),
                buildRollbackUiMessage(failedEventType, failedTraceId),
                null
        );

        try {
            gatewayEventPublishPort.publish(rollbackMessage);
            log.info("Business 발행 실패 보상 이벤트 발행 완료. rollbackEventType=EQP_DELETE, failedEventType={}, failedTraceId={}, eqpId={}",
                    failedEventType, failedTraceId, eqpId);
        } catch (Exception rollbackEx) {
            log.error("보상 이벤트 발행 실패 - 수동 조치 필요. failedEventType={}, failedTraceId={}, eqpId={}",
                    failedEventType, failedTraceId, eqpId, rollbackEx);
        }
    }

    /**
     * 보상 이벤트 추적용 uiMessage를 생성합니다.
     *
     * @param failedEventType 실패한 원본 이벤트 타입
     * @param failedTraceId 실패한 원본 traceId
     * @return 보상 추적용 uiMessage 문자열
     */
    private static String buildRollbackUiMessage(
            final UiCommandEventType failedEventType,
            final String failedTraceId
    ) {
        return ROLLBACK_UI_MESSAGE_PREFIX
                + "|failedEventType=" + failedEventType.name()
                + "|failedTraceId=" + failedTraceId;
    }

    /**
     * DualResponse Future 완료 결과를 DeferredResult로 변환합니다.
     *
     * <p>판정 규칙:</p>
     * <ul>
     *   <li>CancellationException → 취소 경로(예: 발행 실패 후 정리) → 500 PUBLISH_FAILED</li>
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
            final UiCommandEventType eventType,
            final String traceId,
            final String eqpId,
            final UiDualTaskFinalResult result,
            final Throwable throwable
    ) {
        // 4.9 QUALITY-03:
        // 발행 실패 경로에서 이미 즉시 응답을 내려준 경우(또는 타 스레드에서 먼저 완료된 경우)
        // 콜백이 추가로 setResult를 호출하지 않도록 선행 가드합니다.
        if (deferredResult.isSetOrExpired()) {
            if (log.isTraceEnabled()) {
                log.trace("DeferredResult 이미 완료됨 - 후속 완료 콜백 무시. eventType={}, traceId={}, eqpId={}",
                        eventType, traceId, eqpId);
            }
            return;
        }

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

    /**
     * 발행 실패 시 즉시 500(PUBLISH_FAILED) 응답을 반환합니다.
     *
     * <p>기존에는 registry.cancel() → Future CancellationException 콜백을 경유해
     * 에러 응답을 설정했지만, 본 메서드로 즉시 응답을 내려 실패 경로를 단순화합니다.</p>
     *
     * @param deferredResult 응답 설정 대상
     * @param eventType      실패한 이벤트 타입
     * @param traceId        요청 traceId
     * @param eqpId          요청 eqpId
     */
    private void setPublishFailedResultIfPossible(
            final DeferredResult<ResponseEntity<ApiResponse<Void>>> deferredResult,
            final UiCommandEventType eventType,
            final String traceId,
            final String eqpId
    ) {
        if (deferredResult.isSetOrExpired()) {
            if (log.isDebugEnabled()) {
                log.debug("발행 실패 즉시 응답 생략 - 이미 DeferredResult 완료됨. eventType={}, traceId={}, eqpId={}",
                        eventType, traceId, eqpId);
            }
            return;
        }
        deferredResult.setResult(ResponseEntity.internalServerError()
                .body(ApiResponse.error("PUBLISH_FAILED", "Kafka 발행 중 오류가 발생했습니다.")));
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
     * @param message   발행할 UI 명령 메시지
     * @return 202 Accepted + traceId | 500 발행 실패
     */
    private ResponseEntity<ApiResponse<AsyncAcceptResponse>> publishLifecycleAndAccept(
            final UiCommandEventType eventType,
            final String traceId,
            final String eqpId,
            final UiCommandMessage message
    ) {
        // START/END는 polling 기반이므로 발행 전에 먼저 PENDING 상태를 등록합니다.
        // 발행 이전 등록으로 인해 빠른 응답 도착 레이스에서도 404 오인 응답을 방지합니다.
        final long lifecycleTimeoutMs = dualRequestProperties.getDualRequestTimeoutMs();
        asyncResultStorePort.registerPending(traceId, lifecycleTimeoutMs);

        try {
            gatewayEventPublishPort.publish(message);
            log.info("{} Kafka 발행 완료. eqpId={}, traceId={}", eventType, eqpId, traceId);
        } catch (Exception e) {
            log.error("{} Kafka 발행 실패. eqpId={}, traceId={}", eventType, eqpId, traceId, e);
            // 발행 자체가 실패한 traceId는 더 이상 완료될 수 없으므로 즉시 TIMEOUT 상태로 전환합니다.
            asyncResultStorePort.markTimeout(traceId);
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
     * 기술 중립 UI 명령 메시지를 생성합니다.
     *
     * <p>Kafka 직렬화용 envelope(metadata/data) 조립은 Kafka 어댑터 계층에서 수행합니다.</p>
     *
     * @param eventType        이벤트 타입
     * @param traceId          작업 추적 ID
     * @param eqpId            설비 ID
     * @param interfaceType    인터페이스 타입
     * @param uiMessage        UI 메시지 (null 허용)
     * @param equipmentProfile 설비 프로파일 스냅샷 (null 허용 — DELETE/START/END 시 생략)
     * @return 생성된 UiCommandMessage
     */
    private UiCommandMessage buildMessage(
            final UiCommandEventType eventType,
            final String traceId,
            final String eqpId,
            final String interfaceType,
            final String uiMessage,
            final GatewayEquipmentProfileSnapshot equipmentProfile
    ) {
        return new UiCommandMessage(
                eventType,
                traceId,
                TcKafkaSources.UI_BACKEND,
                eqpId,
                interfaceType,
                uiMessage,
                equipmentProfile
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

    /**
     * 설비 도메인 페이지를 응답 DTO 페이지로 변환합니다.
     *
     * @param page 도메인 페이지 응답
     * @return API 응답 페이지
     */
    private static PagedResponse<EqpInfoResponse> toEqpPage(final PagedResponse<TcEqp> page) {
        return PagedResponse.of(
                page.items().stream()
                        .map(EqpController::toEqpInfoResponse)
                        .toList(),
                page.offset(),
                page.limit(),
                page.count()
        );
    }

    /**
     * 설비 도메인을 응답 DTO로 변환합니다.
     *
     * @param eqp 설비 도메인 객체
     * @return 응답 DTO
     */
    private static EqpInfoResponse toEqpInfoResponse(final TcEqp eqp) {
        return new EqpInfoResponse(
                eqp.eqpKey(),
                eqp.eqpId(),
                eqp.commInterface(),
                eqp.commMode(),
                eqp.isDev(),
                eqp.routePartition(),
                eqp.eqpIp(),
                eqp.eqpPort(),
                eqp.modelVersionKey(),
                eqp.enabled(),
                eqp.createdAt(),
                eqp.updatedAt(),
                eqp.createdBy(),
                eqp.updatedBy()
        );
    }

    /**
     * 현재 스레드 MDC에 traceId를 주입하고 스코프 종료 시 이전 값으로 복구합니다.
     *
     * <p>컨트롤러/콜백 경계에서 traceId 상관관계를 유지하기 위한 최소 스코프 헬퍼입니다.</p>
     *
     * @param traceId 주입할 traceId
     * @return try-with-resources로 사용할 MDC 스코프
     */
    private static MdcTraceScope openTraceMdcScope(final String traceId) {
        final String previousTraceId = MDC.get(TRACE_ID_MDC_KEY);
        if (traceId == null || traceId.isBlank()) {
            MDC.remove(TRACE_ID_MDC_KEY);
        } else {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
        }
        return new MdcTraceScope(previousTraceId);
    }

    /**
     * traceId MDC 복구를 담당하는 AutoCloseable 스코프입니다.
     *
     * @param previousTraceId 스코프 진입 전 traceId
     */
    private record MdcTraceScope(String previousTraceId) implements AutoCloseable {
        @Override
        public void close() {
            if (previousTraceId == null || previousTraceId.isBlank()) {
                MDC.remove(TRACE_ID_MDC_KEY);
                return;
            }
            MDC.put(TRACE_ID_MDC_KEY, previousTraceId);
        }
    }
}
