package com.nori.tc.ui.adapters.web.controller;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.ui.adapters.web.controller.support.UiPageRequestSupport;
import com.nori.tc.ui.adapters.web.dto.request.ModelUpsertRequest;
import com.nori.tc.ui.adapters.web.dto.response.ApiResponse;
import com.nori.tc.ui.adapters.web.dto.response.ModelInfoResponse;
import com.nori.tc.ui.core.model.PagedResponse;
import com.nori.tc.ui.core.port.db.ModelCrudPort;
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

import java.util.List;
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

    private final ModelCrudPort modelCrudPort;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param modelCrudPort 모델 CRUD 포트
     */
    public ModelController(final ModelCrudPort modelCrudPort) {
        this.modelCrudPort = Objects.requireNonNull(modelCrudPort, "modelCrudPort is null");
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

        if (modelCrudPort.findByModelVersionKey(modelVersionKey).isEmpty()) {
            log.warn("모델 수정 대상 없음. modelVersionKey={}", modelVersionKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "수정할 모델을 찾을 수 없습니다."));
        }

        final UpsertTcModel command = new UpsertTcModel(
                modelVersionKey,
                request.modelName(),
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
