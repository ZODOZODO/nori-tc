package com.nori.tc.ui.adapters.web.controller;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import com.nori.tc.db.domain.model.TcModelEventId;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.model.TcModelParam;
import com.nori.tc.db.domain.model.TcModelReportId;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelVariableId;
import com.nori.tc.db.domain.model.TcModelWorkflow;
import com.nori.tc.ui.adapters.web.controller.support.UiPageRequestSupport;
import com.nori.tc.ui.adapters.web.dto.request.ModelUpsertRequest;
import com.nori.tc.ui.adapters.web.dto.response.ApiResponse;
import com.nori.tc.ui.adapters.web.dto.response.ModelDetailDataResponse;
import com.nori.tc.ui.adapters.web.dto.response.ModelDetailRowResponse;
import com.nori.tc.ui.adapters.web.dto.response.ModelInfoResponse;
import com.nori.tc.ui.adapters.web.dto.response.ModelMdfContentResponse;
import com.nori.tc.ui.core.model.PagedResponse;
import com.nori.tc.ui.core.port.db.ModelCrudPort;
import com.nori.tc.ui.core.port.db.ModelDetailQueryPort;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 모델 정보(Model Info) 관리 REST API 컨트롤러입니다.
 *
 * <p>제공 엔드포인트:</p>
 * <ul>
 *   <li>GET /api/model</li>
 *   <li>GET /api/model/{modelVersionKey}</li>
 *   <li>POST /api/model</li>
 *   <li>PUT /api/model/{modelVersionKey}</li>
 *   <li>DELETE /api/model/{modelVersionKey}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/model")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);
    private static final List<String> MODEL_PARAMETER_COLUMNS = List.of(
            "Parameter Name",
            "Parameter Value",
            "Description"
    );
    private static final List<String> SECS_MESSAGE_COLUMNS = List.of(
            "SECS Message Name",
            "Description",
            "Data Indexes"
    );
    private static final List<String> SOCKET_MESSAGE_COLUMNS = List.of(
            "Socket Message Name",
            "Description",
            "Data Indexes"
    );
    private static final List<String> VARIABLE_ID_COLUMNS = List.of(
            "VariableId",
            "VID Type",
            "Description"
    );
    private static final List<String> REPORT_ID_COLUMNS = List.of(
            "ReportId",
            "VariableIdes",
            "Enabled",
            "Description"
    );
    private static final List<String> EVENT_ID_COLUMNS = List.of(
            "EventId",
            "ReportIdes",
            "Enabled",
            "Description"
    );
    private static final List<String> WORKFLOW_COLUMNS = List.of(
            "Workflow Name",
            "Message Name",
            "EventId",
            "TransactionId",
            "Filter",
            "Action Name",
            "Data Index"
    );
    private static final List<String> DCOP_ITEM_COLUMNS = List.of(
            "Dcop Item Name",
            "Workflow Name",
            "EventId",
            "VariableId",
            "Collection Rule",
            "Order Rule"
    );

    private final ModelCrudPort modelCrudPort;
    private final ModelDetailQueryPort modelDetailQueryPort;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param modelCrudPort 모델 CRUD 포트
     * @param modelDetailQueryPort 모델 상세 조회 포트
     */
    public ModelController(
            final ModelCrudPort modelCrudPort,
            final ModelDetailQueryPort modelDetailQueryPort
    ) {
        this.modelCrudPort = Objects.requireNonNull(modelCrudPort, "modelCrudPort is null");
        this.modelDetailQueryPort = Objects.requireNonNull(modelDetailQueryPort, "modelDetailQueryPort is null");
    }

    /**
     * 모델 목록을 페이지 단위로 조회합니다.
     *
     * @param offset 조회 시작 위치(기본값 0)
     * @param limit  조회 건수(기본값 100, 최대 500)
     * @return 목록 페이지 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ModelInfoResponse>>> list(
            @RequestParam(name = "offset", required = false) final Integer offset,
            @RequestParam(name = "limit", required = false) final Integer limit
    ) {
        final PageRequest pageRequest = UiPageRequestSupport.resolve(offset, limit);

        if (log.isDebugEnabled()) {
            log.debug("모델 목록 조회 요청. offset={}, limit={}", pageRequest.offset(), pageRequest.limit());
        }

        final PagedResponse<TcModel> page = modelCrudPort.findAll(pageRequest);
        final PagedResponse<ModelInfoResponse> responsePage = toModelPage(page);

        if (log.isDebugEnabled()) {
            log.debug("모델 목록 조회 완료. offset={}, limit={}, pageSize={}, totalCount={}",
                    responsePage.offset(), responsePage.limit(), responsePage.items().size(), responsePage.count());
        }

        return ResponseEntity.ok(ApiResponse.success(responsePage));
    }

    /**
     * 모델 버전 키 기준으로 단건을 조회합니다.
     *
     * @param modelVersionKey 모델 버전 키
     * @return 단건 조회 응답
     */
    @GetMapping("/{modelVersionKey}")
    public ResponseEntity<ApiResponse<ModelInfoResponse>> get(
            @PathVariable final long modelVersionKey
    ) {
        if (log.isDebugEnabled()) {
            log.debug("모델 단건 조회 요청. modelVersionKey={}", modelVersionKey);
        }

        final Optional<TcModel> optionalModel = modelCrudPort.findByModelVersionKey(modelVersionKey);
        if (optionalModel.isEmpty()) {
            log.warn("모델 단건 조회 결과 없음. modelVersionKey={}", modelVersionKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "모델을 찾을 수 없습니다."));
        }

        return ResponseEntity.ok(ApiResponse.success(toModelInfoResponse(optionalModel.get())));
    }

    /**
     * Model 상세 노드 데이터를 조회합니다.
     *
     * <p>노드별 매핑:</p>
     * <ul>
     *   <li>model-parameter -> tc_model_param</li>
     *   <li>secs-message -> tc_model_secs_message</li>
     *   <li>socket-message -> tc_model_socket_message</li>
     *   <li>variableides -> tc_model_variableid</li>
     *   <li>reportides -> tc_model_reportid</li>
     *   <li>eventides -> tc_model_eventid</li>
     *   <li>workflow -> tc_model_workflow</li>
     *   <li>mdf -> tc_model_mdf</li>
     *   <li>dcop-itemes -> tc_model_dcop_item</li>
     * </ul>
     *
     * @param modelVersionKey 모델 버전 키
     * @param detailNode 상세 노드 식별자
     * @return 상세 노드 데이터 응답
     */
    @GetMapping("/{modelVersionKey}/details/{detailNode}")
    public ResponseEntity<ApiResponse<ModelDetailDataResponse>> getDetail(
            @PathVariable final long modelVersionKey,
            @PathVariable final String detailNode
    ) {
        if (log.isDebugEnabled()) {
            log.debug("모델 상세 노드 조회 요청. modelVersionKey={}, detailNode={}", modelVersionKey, detailNode);
        }

        if (modelCrudPort.findByModelVersionKey(modelVersionKey).isEmpty()) {
            log.warn("모델 상세 노드 조회 결과 없음 - 모델 미존재. modelVersionKey={}, detailNode={}", modelVersionKey, detailNode);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "모델을 찾을 수 없습니다."));
        }

        final String normalizedNode = (detailNode == null) ? "" : detailNode.trim().toLowerCase(Locale.ROOT);
        final ModelDetailDataResponse response = switch (normalizedNode) {
            case "model-parameter" -> new ModelDetailDataResponse(
                    MODEL_PARAMETER_COLUMNS,
                    modelDetailQueryPort.findParamsByModelVersionKey(modelVersionKey).stream()
                            .map(ModelController::toModelParamRow)
                            .toList(),
                    List.of()
            );
            case "secs-message" -> new ModelDetailDataResponse(
                    SECS_MESSAGE_COLUMNS,
                    modelDetailQueryPort.findSecsMessagesByModelVersionKey(modelVersionKey).stream()
                            .map(ModelController::toSecsMessageRow)
                            .toList(),
                    List.of()
            );
            case "socket-message" -> new ModelDetailDataResponse(
                    SOCKET_MESSAGE_COLUMNS,
                    modelDetailQueryPort.findSocketMessagesByModelVersionKey(modelVersionKey).stream()
                            .map(ModelController::toSocketMessageRow)
                            .toList(),
                    List.of()
            );
            case "variableides" -> new ModelDetailDataResponse(
                    VARIABLE_ID_COLUMNS,
                    modelDetailQueryPort.findVariableIdsByModelVersionKey(modelVersionKey).stream()
                            .map(ModelController::toVariableIdRow)
                            .toList(),
                    List.of()
            );
            case "reportides" -> new ModelDetailDataResponse(
                    REPORT_ID_COLUMNS,
                    modelDetailQueryPort.findReportIdsByModelVersionKey(modelVersionKey).stream()
                            .map(ModelController::toReportIdRow)
                            .toList(),
                    List.of()
            );
            case "eventides" -> new ModelDetailDataResponse(
                    EVENT_ID_COLUMNS,
                    modelDetailQueryPort.findEventIdsByModelVersionKey(modelVersionKey).stream()
                            .map(ModelController::toEventIdRow)
                            .toList(),
                    List.of()
            );
            case "workflow" -> new ModelDetailDataResponse(
                    WORKFLOW_COLUMNS,
                    modelDetailQueryPort.findWorkflowsByModelVersionKey(modelVersionKey).stream()
                            .map(ModelController::toWorkflowRow)
                            .toList(),
                    List.of()
            );
            case "mdf" -> new ModelDetailDataResponse(
                    List.of(),
                    List.of(),
                    modelDetailQueryPort.findMdfByModelVersionKey(modelVersionKey)
                            .map(ModelController::toMdfContent)
                            .stream()
                            .toList()
            );
            case "dcop-itemes" -> new ModelDetailDataResponse(
                    DCOP_ITEM_COLUMNS,
                    modelDetailQueryPort.findDcopItemsByModelVersionKey(modelVersionKey).stream()
                            .map(ModelController::toDcopItemRow)
                            .toList(),
                    List.of()
            );
            default -> null;
        };

        if (response == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_NODE", "지원하지 않는 상세 노드입니다."));
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 모델을 등록합니다.
     *
     * @param request 등록 요청 본문
     * @return 등록된 모델 정보
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ModelInfoResponse>> create(
            @Valid @RequestBody final ModelUpsertRequest request
    ) {
        log.info("모델 등록 요청. modelName={}, modelVersion={}, commInterface={}, status={}",
                request.modelName(), request.modelVersion(), request.commInterface(), request.status());

        final UpsertTcModel command = new UpsertTcModel(
                null,
                request.modelName(),
                null,
                request.modelVersion(),
                request.commInterface(),
                request.status(),
                request.description(),
                request.maker(),
                request.createdBy(),
                request.updatedBy()
        );

        final TcModel created = modelCrudPort.upsert(command);
        log.info("모델 등록 완료. modelVersionKey={}, modelName={}, modelVersion={}",
                created.modelVersionKey(), created.modelName(), created.modelVersion());

        return ResponseEntity.ok(ApiResponse.success(toModelInfoResponse(created)));
    }

    /**
     * 모델 정보를 수정합니다.
     *
     * @param modelVersionKey 수정 대상 모델 버전 키
     * @param request 수정 요청 본문
     * @return 수정된 모델 정보
     */
    @PutMapping("/{modelVersionKey}")
    public ResponseEntity<ApiResponse<ModelInfoResponse>> update(
            @PathVariable final long modelVersionKey,
            @Valid @RequestBody final ModelUpsertRequest request
    ) {
        log.info("모델 수정 요청. modelVersionKey={}, modelName={}, modelVersion={}",
                modelVersionKey, request.modelName(), request.modelVersion());

        final Optional<TcModel> existingModel = modelCrudPort.findByModelVersionKey(modelVersionKey);
        if (existingModel.isEmpty()) {
            log.warn("모델 수정 대상 없음. modelVersionKey={}", modelVersionKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "수정할 모델을 찾을 수 없습니다."));
        }
        final TcModel existing = existingModel.get();

        final UpsertTcModel command = new UpsertTcModel(
                modelVersionKey,
                request.modelName(),
                existing.parentModel(),
                request.modelVersion(),
                request.commInterface(),
                request.status(),
                request.description(),
                request.maker(),
                request.createdBy(),
                request.updatedBy()
        );

        final TcModel updated = modelCrudPort.upsert(command);
        log.info("모델 수정 완료. modelVersionKey={}, modelName={}, modelVersion={}",
                updated.modelVersionKey(), updated.modelName(), updated.modelVersion());

        return ResponseEntity.ok(ApiResponse.success(toModelInfoResponse(updated)));
    }

    /**
     * 모델을 삭제합니다.
     *
     * @param modelVersionKey 삭제 대상 모델 버전 키
     * @return 삭제 성공 응답
     */
    @DeleteMapping("/{modelVersionKey}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable final long modelVersionKey
    ) {
        log.info("모델 삭제 요청. modelVersionKey={}", modelVersionKey);
        modelCrudPort.deleteByModelVersionKey(modelVersionKey);
        log.info("모델 삭제 완료. modelVersionKey={}", modelVersionKey);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * tc_model_param row를 상세 테이블 row DTO로 변환합니다.
     */
    private static ModelDetailRowResponse toModelParamRow(final TcModelParam param) {
        return new ModelDetailRowResponse(List.of(
                nullToEmpty(param.paramName()),
                nullToEmpty(param.paramValue()),
                nullToEmpty(param.description())
        ));
    }

    /**
     * tc_model_secs_message row를 상세 테이블 row DTO로 변환합니다.
     */
    private static ModelDetailRowResponse toSecsMessageRow(final TcModelSecsMessage message) {
        return new ModelDetailRowResponse(List.of(
                nullToEmpty(message.secsMsgName()),
                nullToEmpty(message.description()),
                nullToEmpty(message.dataIndex())
        ));
    }

    /**
     * tc_model_socket_message row를 상세 테이블 row DTO로 변환합니다.
     */
    private static ModelDetailRowResponse toSocketMessageRow(final TcModelSocketMessage message) {
        return new ModelDetailRowResponse(List.of(
                nullToEmpty(message.socketMsgName()),
                nullToEmpty(message.description()),
                nullToEmpty(message.dataIndex())
        ));
    }

    /**
     * tc_model_variableid row를 상세 테이블 row DTO로 변환합니다.
     */
    private static ModelDetailRowResponse toVariableIdRow(final TcModelVariableId variableId) {
        final String variableIdType = variableId.variableIdType() == null ? "" : variableId.variableIdType().name();
        return new ModelDetailRowResponse(List.of(
                nullToEmpty(variableId.variableId()),
                variableIdType,
                nullToEmpty(variableId.description())
        ));
    }

    /**
     * tc_model_reportid row를 상세 테이블 row DTO로 변환합니다.
     */
    private static ModelDetailRowResponse toReportIdRow(final TcModelReportId reportId) {
        return new ModelDetailRowResponse(List.of(
                nullToEmpty(reportId.reportId()),
                nullToEmpty(reportId.variableId()),
                Boolean.toString(reportId.enabled()),
                nullToEmpty(reportId.description())
        ));
    }

    /**
     * tc_model_eventid row를 상세 테이블 row DTO로 변환합니다.
     */
    private static ModelDetailRowResponse toEventIdRow(final TcModelEventId eventId) {
        return new ModelDetailRowResponse(List.of(
                nullToEmpty(eventId.eventId()),
                nullToEmpty(eventId.reportId()),
                Boolean.toString(eventId.enabled()),
                nullToEmpty(eventId.description())
        ));
    }

    /**
     * tc_model_workflow row를 상세 테이블 row DTO로 변환합니다.
     */
    private static ModelDetailRowResponse toWorkflowRow(final TcModelWorkflow workflow) {
        return new ModelDetailRowResponse(List.of(
                nullToEmpty(workflow.workflowName()),
                nullToEmpty(workflow.messageName()),
                nullToEmpty(workflow.eventId()),
                nullToEmpty(workflow.transactionId()),
                nullToEmpty(workflow.workflowFilter()),
                nullToEmpty(workflow.actionName()),
                nullToEmpty(workflow.actionDataIndex())
        ));
    }

    /**
     * tc_model_dcop_item row를 상세 테이블 row DTO로 변환합니다.
     */
    private static ModelDetailRowResponse toDcopItemRow(final TcModelDcopItem dcopItem) {
        final String collectionRule = dcopItem.collectionRule() == null ? "" : dcopItem.collectionRule().name();
        final String orderRule = dcopItem.orderRule() == null ? "" : dcopItem.orderRule().toString();
        return new ModelDetailRowResponse(List.of(
                nullToEmpty(dcopItem.dcopItemName()),
                nullToEmpty(dcopItem.workflowName()),
                nullToEmpty(dcopItem.eventId()),
                nullToEmpty(dcopItem.variableId()),
                collectionRule,
                orderRule
        ));
    }

    /**
     * tc_model_mdf row를 MDF(XML) DTO로 변환합니다.
     */
    private static ModelMdfContentResponse toMdfContent(final TcModelMdf mdf) {
        final byte[] mdfFile = (mdf.mdfFile() == null) ? new byte[0] : mdf.mdfFile();
        final String xml = new String(mdfFile, StandardCharsets.UTF_8);
        return new ModelMdfContentResponse(nullToEmpty(mdf.mdfName()), xml);
    }

    /**
     * null 문자열을 빈 문자열로 변환합니다.
     */
    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }

    /**
     * 도메인 페이지 응답을 API 응답 DTO 페이지로 변환합니다.
     *
     * @param page 도메인 페이지 응답
     * @return API 응답 페이지
     */
    private static PagedResponse<ModelInfoResponse> toModelPage(final PagedResponse<TcModel> page) {
        final List<ModelInfoResponse> items = page.items().stream()
                .map(ModelController::toModelInfoResponse)
                .toList();
        return PagedResponse.of(items, page.offset(), page.limit(), page.count());
    }

    /**
     * 도메인 모델을 응답 DTO로 변환합니다.
     *
     * @param model 도메인 모델
     * @return 응답 DTO
     */
    private static ModelInfoResponse toModelInfoResponse(final TcModel model) {
        return new ModelInfoResponse(
                model.modelVersionKey(),
                model.modelKey(),
                model.modelName(),
                model.parentModel(),
                model.modelVersion(),
                model.commInterface(),
                model.status(),
                model.description(),
                model.maker(),
                model.createdAt(),
                model.updatedAt(),
                model.createdBy(),
                model.updatedBy()
        );
    }
}
