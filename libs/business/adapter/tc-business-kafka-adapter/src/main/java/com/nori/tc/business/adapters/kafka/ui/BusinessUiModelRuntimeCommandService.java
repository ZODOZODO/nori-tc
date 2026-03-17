package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.logging.BusinessLogContext;
import com.nori.tc.business.core.modelcache.BusinessModelParamMutationPort;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.core.ui.BusinessUiTaskErrorCode;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI 이벤트를 기반으로 model runtime 캐시와 model/eqp param 캐시를 제어하는 서비스입니다.
 *
 * <p>지원 시나리오:</p>
 * <p>1) EQP_CREATE / EQP_UPDATE: eqpId -> modelVersionKey 바인딩 갱신 + model/eqp param 캐시 갱신</p>
 * <p>2) EQP_UPDATE_JARFILE: model runtime 리로드 + model param 캐시 갱신 + workflow plugin runtime 리로드</p>
 * <p>3) EQP_DELETE: eqpId 바인딩 제거 + workflow plugin runtime 제거</p>
 *
 * <p>파라미터 캐시 갱신 정책:</p>
 * <ul>
 *   <li>EQP_CREATE / EQP_UPDATE: model runtime 바인딩 갱신 이후 model params와 eqp params를 모두 갱신합니다.
 *       model params는 modelVersionKey 기준으로, eqp params는 DB의 현재 appliedParamVersion 기준으로 갱신합니다.</li>
 *   <li>EQP_UPDATE_JARFILE: 새 JAR 반영 후 model runtime을 갱신하면서 model params도 함께 갱신합니다.
 *       JAR 업데이트 시 model param이 변경될 수 있기 때문입니다.</li>
 *   <li>EQP_DELETE: 파라미터 캐시를 명시적으로 정리하지 않습니다.
 *       eqpKeyBindings에서 제거된 eqpId는 이후 param 조회 시 WARN 후 empty를 반환하므로 안전합니다.</li>
 * </ul>
 *
 * <p>파라미터 캐시 갱신 실패는 model runtime 갱신 실패와 독립적으로 처리됩니다.
 * runtime 바인딩이 성공하면 PASS를 반환하되, param 갱신 실패는 별도 FAIL로 응답합니다.</p>
 */
@Service
public class BusinessUiModelRuntimeCommandService {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiModelRuntimeCommandService.class);

    /**
     * 키-값 텍스트 파싱 패턴입니다.
     *
     * <p>신규 키(`modelVersionKey`)와 구키(`modelKey`)를 모두 허용해
     * 운영 중 점진적 전환 시 호환성을 보장합니다.</p>
     */
    private static final Pattern MODEL_VERSION_KEY_KV_PATTERN =
            Pattern.compile("(?i)\\b(?:modelVersionKey|modelKey)\\s*=\\s*([0-9]+)\\b");

    private final BusinessModelRuntimeMutationPort runtimeMutationPort;
    private final BusinessModelParamMutationPort paramMutationPort;
    private final BusinessWorkflowPluginRuntimeMutationPort pluginRuntimeMutationPort;
    private final ObjectMapper objectMapper;

    /**
     * 필수 의존성을 주입합니다.
     */
    public BusinessUiModelRuntimeCommandService(
            final BusinessModelRuntimeMutationPort runtimeMutationPort,
            final BusinessModelParamMutationPort paramMutationPort,
            final BusinessWorkflowPluginRuntimeMutationPort pluginRuntimeMutationPort,
            final ObjectMapper objectMapper
    ) {
        this.runtimeMutationPort = Objects.requireNonNull(runtimeMutationPort, "runtimeMutationPort is null");
        this.paramMutationPort = Objects.requireNonNull(paramMutationPort, "paramMutationPort is null");
        this.pluginRuntimeMutationPort = Objects.requireNonNull(pluginRuntimeMutationPort, "pluginRuntimeMutationPort is null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
    }

    /**
     * EQP_CREATE / EQP_UPDATE 이벤트를 처리합니다.
     *
     * <p>처리 순서:
     * <ol>
     *   <li>uiMessage에서 modelVersionKey를 추출하여 eqp 바인딩(runtime)을 갱신합니다.</li>
     *   <li>갱신된 eqpKey 기준으로 model params 캐시를 갱신합니다.
     *       새 modelVersionKey로 바인딩되면 해당 모델의 param도 최신화가 필요합니다.</li>
     *   <li>갱신된 eqpKey 기준으로 eqp params 캐시를 갱신합니다.
     *       설비 설정 변경(appliedParamVersion 등)이 있을 수 있습니다.</li>
     * </ol>
     * </p>
     *
     * <p>runtime 바인딩 실패 시 param 갱신도 중단합니다.
     * model param 갱신 실패 시 eqp param 갱신도 중단합니다.</p>
     */
    public KafkaTaskResult handleEqpCreateOrUpdate(final KafkaUiTaskMessage request) {
        final String eqpId = normalize(request.data().eqpId());
        final String traceId = normalize(request.metadata().traceId());
        final String eventType = request.metadata().eventType();
        if (eqpId == null) {
            return KafkaTaskResult.fail(BusinessUiTaskErrorCode.EQP_ID_REQUIRED, "eqpId는 필수입니다.");
        }

        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(eqpId, traceId)) {
            log.info("UI {} request received. eqpId={}, traceId={}", eventType, eqpId, traceId);

            final Long modelVersionKey = resolveModelVersionKey(request.data().uiMessage());
            if (modelVersionKey == null) {
                log.warn("UI {} failed: modelVersionKey not found. eqpId={}, traceId={}", eventType, eqpId, traceId);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_KEY_REQUIRED,
                        "uiMessage에서 modelVersionKey를 찾을 수 없습니다."
                );
            }

            // 1. eqpId-modelVersionKey 바인딩 갱신 (model runtime 캐시 포함)
            try {
                runtimeMutationPort.updateEqpBinding(eqpId, modelVersionKey);
            } catch (Exception ex) {
                log.error("UI {} failed during binding update. eqpId={}, traceId={}, modelVersionKey={}",
                        eventType, eqpId, traceId, modelVersionKey, ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_RUNTIME_UPDATE_FAILED,
                        "eqpId-modelVersionKey 바인딩 갱신에 실패했습니다."
                );
            }

            // 2. model param 캐시 갱신
            // eqpId가 새 modelVersionKey에 바인딩되었으므로 해당 모델의 param을 최신화합니다.
            try {
                paramMutationPort.reloadModelParams(modelVersionKey);
            } catch (Exception ex) {
                log.error("UI {} failed during model param reload. eqpId={}, traceId={}, modelVersionKey={}",
                        eventType, eqpId, traceId, modelVersionKey, ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_PARAM_RELOAD_FAILED,
                        "model 파라미터 캐시 갱신에 실패했습니다."
                );
            }

            // 3. eqp param 캐시 갱신
            // 설비의 appliedParamVersion이 변경되었을 수 있으므로 eqp param을 최신화합니다.
            final Optional<Long> eqpKey = runtimeMutationPort.findEqpKeyByEqpId(eqpId);
            if (eqpKey.isPresent()) {
                try {
                    paramMutationPort.reloadEqpParams(eqpKey.get());
                } catch (Exception ex) {
                    log.error("UI {} failed during eqp param reload. eqpId={}, traceId={}, eqpKey={}",
                            eventType, eqpId, traceId, eqpKey.get(), ex);
                    return KafkaTaskResult.fail(
                            BusinessUiTaskErrorCode.EQP_PARAM_RELOAD_FAILED,
                            "eqp 파라미터 캐시 갱신에 실패했습니다."
                    );
                }
            } else {
                // eqpKeyBindings에 없으면 runtime 적재 직후임에도 eqpKey를 못 찾은 것 → WARN만 남기고 계속
                log.warn("UI {} eqp param reload skipped: eqpKey not found after binding update. eqpId={}, traceId={}",
                        eventType, eqpId, traceId);
            }

            log.info("UI {} success. eqpId={}, traceId={}, modelVersionKey={}",
                    eventType, eqpId, traceId, modelVersionKey);
            if (log.isDebugEnabled()) {
                log.debug("UI {} binding and param update detail. eqpId={}, modelVersionKey={}",
                        eventType, eqpId, modelVersionKey);
            }
            return KafkaTaskResult.pass();
        }
    }

    /**
     * EQP_UPDATE_JARFILE 이벤트를 처리합니다.
     *
     * <p>처리 순서:
     * <ol>
     *   <li>uiMessage의 modelVersionKey 또는 기존 바인딩에서 modelVersionKey를 확인합니다.</li>
     *   <li>model runtime 캐시를 리로드합니다 (새 JAR의 워크플로우 정의 반영).</li>
     *   <li>model param 캐시를 갱신합니다 (새 JAR에 따라 model param이 변경될 수 있음).</li>
     *   <li>workflow plugin runtime을 리로드합니다.</li>
     * </ol>
     * </p>
     *
     * <p>modelVersionKey 결정 우선순위:</p>
     * <p>1) uiMessage의 modelVersionKey</p>
     * <p>2) eqpId 바인딩에 저장된 modelVersionKey</p>
     */
    public KafkaTaskResult handleEqpUpdateJarfile(final KafkaUiTaskMessage request) {
        final String eqpId = normalize(request.data().eqpId());
        final String traceId = normalize(request.metadata().traceId());
        if (eqpId == null) {
            return KafkaTaskResult.fail(BusinessUiTaskErrorCode.EQP_ID_REQUIRED, "eqpId는 필수입니다.");
        }

        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(eqpId, traceId)) {
            log.info("UI EQP_UPDATE_JARFILE request received. eqpId={}, traceId={}", eqpId, traceId);

            final Long modelVersionKeyFromMessage = resolveModelVersionKey(request.data().uiMessage());
            final Long modelVersionKey = modelVersionKeyFromMessage != null
                    ? modelVersionKeyFromMessage
                    : runtimeMutationPort.findModelVersionKeyByEqpId(eqpId).orElse(null);

            if (modelVersionKey == null) {
                log.warn("UI EQP_UPDATE_JARFILE failed: model binding not found. eqpId={}, traceId={}", eqpId, traceId);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_BINDING_NOT_FOUND,
                        "eqpId에 매핑된 modelVersionKey를 찾을 수 없습니다."
                );
            }

            // 1. model runtime 캐시 리로드 (새 JAR의 워크플로우 정의 반영)
            try {
                runtimeMutationPort.reloadModelRuntime(modelVersionKey);
            } catch (Exception ex) {
                log.error("UI EQP_UPDATE_JARFILE failed during runtime reload. eqpId={}, traceId={}, modelVersionKey={}",
                        eqpId, traceId, modelVersionKey, ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_RUNTIME_UPDATE_FAILED,
                        "model runtime 리로드에 실패했습니다."
                );
            }

            // 2. model param 캐시 갱신
            // 새 JAR 반영 시 model param이 변경될 수 있으므로 함께 갱신합니다.
            try {
                paramMutationPort.reloadModelParams(modelVersionKey);
            } catch (Exception ex) {
                log.error("UI EQP_UPDATE_JARFILE failed during model param reload. eqpId={}, traceId={}, modelVersionKey={}",
                        eqpId, traceId, modelVersionKey, ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_PARAM_RELOAD_FAILED,
                        "model 파라미터 캐시 갱신에 실패했습니다."
                );
            }

            // 3. workflow plugin runtime 리로드
            try {
                pluginRuntimeMutationPort.reloadByEqpId(eqpId);
            } catch (Exception ex) {
                log.error("UI EQP_UPDATE_JARFILE failed during plugin runtime reload. eqpId={}, traceId={}, modelVersionKey={}",
                        eqpId, traceId, modelVersionKey, ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.WORKFLOW_PLUGIN_RELOAD_FAILED,
                        "workflow plugin runtime 리로드에 실패했습니다."
                );
            }

            log.info("UI EQP_UPDATE_JARFILE success. eqpId={}, traceId={}, modelVersionKey={}",
                    eqpId, traceId, modelVersionKey);
            if (log.isDebugEnabled()) {
                log.debug("UI EQP_UPDATE_JARFILE detail. eqpId={}, modelVersionKey={}", eqpId, modelVersionKey);
            }
            return KafkaTaskResult.pass();
        }
    }

    /**
     * EQP_DELETE 이벤트를 처리합니다.
     *
     * <p>처리 순서:</p>
     * <p>1) eqp 바인딩 제거</p>
     * <p>2) workflow plugin runtime 제거</p>
     */
    public KafkaTaskResult handleEqpDelete(final KafkaUiTaskMessage request) {
        final String eqpId = normalize(request.data().eqpId());
        final String traceId = normalize(request.metadata().traceId());
        if (eqpId == null) {
            return KafkaTaskResult.fail(BusinessUiTaskErrorCode.EQP_ID_REQUIRED, "eqpId는 필수입니다.");
        }

        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(eqpId, traceId)) {
            log.info("UI EQP_DELETE request received. eqpId={}, traceId={}", eqpId, traceId);

            final Optional<Long> removedModelVersionKey;
            try {
                removedModelVersionKey = runtimeMutationPort.removeEqpBinding(eqpId);
            } catch (Exception ex) {
                log.error("UI EQP_DELETE failed during binding remove. eqpId={}, traceId={}", eqpId, traceId, ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_BINDING_DELETE_FAILED,
                        "eqpId-modelVersionKey 바인딩 제거에 실패했습니다."
                );
            }

            if (removedModelVersionKey.isEmpty()) {
                log.warn("UI EQP_DELETE failed: model binding not found. eqpId={}, traceId={}", eqpId, traceId);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_BINDING_NOT_FOUND,
                        "삭제할 eqpId-modelVersionKey 바인딩이 존재하지 않습니다."
                );
            }

            try {
                pluginRuntimeMutationPort.removeByEqpId(eqpId);
            } catch (Exception ex) {
                log.error("UI EQP_DELETE failed during plugin runtime remove. eqpId={}, traceId={}", eqpId, traceId, ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.WORKFLOW_PLUGIN_REMOVE_FAILED,
                        "workflow plugin runtime 제거에 실패했습니다."
                );
            }

            log.info("UI EQP_DELETE success. eqpId={}, traceId={}, removedModelVersionKey={}",
                    eqpId, traceId, removedModelVersionKey.orElse(null));
            if (log.isDebugEnabled()) {
                log.debug("UI EQP_DELETE detail. eqpId={}, removedModelVersionKey={}",
                        eqpId, removedModelVersionKey.orElse(null));
            }
            return KafkaTaskResult.pass();
        }
    }

    /**
     * uiMessage에서 modelVersionKey를 추출합니다.
     *
     * <p>지원 포맷:</p>
     * <p>1) JSON: {"modelVersionKey":123} 또는 {"modelKey":123}</p>
     * <p>2) 중첩 JSON: {"data":{"modelVersionKey":123}} 또는 {"data":{"modelKey":123}}</p>
     * <p>3) 숫자 문자열: "123"</p>
     * <p>4) key=value 문자열: "modelVersionKey=123" 또는 "modelKey=123"</p>
     */
    Long resolveModelVersionKey(final String uiMessage) {
        final String normalized = normalize(uiMessage);
        if (normalized == null) {
            return null;
        }

        try {
            final JsonNode root = objectMapper.readTree(normalized);
            final Long fromJson = extractModelVersionKeyFromJson(root);
            if (fromJson != null) {
                if (log.isDebugEnabled()) {
                    log.debug("modelVersionKey resolved from JSON. modelVersionKey={}, uiMessage={}",
                            fromJson, normalized);
                }
                return fromJson;
            }
        } catch (Exception ignored) {
            // JSON 파싱 실패는 예외로 처리하지 않고 다음 전략으로 진행합니다.
        }

        final Long plainNumber = parsePositiveLong(normalized);
        if (plainNumber != null) {
            if (log.isDebugEnabled()) {
                log.debug("modelVersionKey resolved from plain number. modelVersionKey={}", plainNumber);
            }
            return plainNumber;
        }

        final Matcher matcher = MODEL_VERSION_KEY_KV_PATTERN.matcher(normalized);
        if (matcher.find()) {
            final Long fromKv = parsePositiveLong(matcher.group(1));
            if (fromKv != null) {
                if (log.isDebugEnabled()) {
                    log.debug("modelVersionKey resolved from key-value text. modelVersionKey={}, text={}",
                            fromKv, normalized);
                }
                return fromKv;
            }
        }

        return null;
    }

    /**
     * JSON 루트/중첩 경로에서 modelVersionKey를 추출합니다.
     */
    private Long extractModelVersionKeyFromJson(final JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        final Long direct = firstPositive(
                parsePositiveLong(root.path("modelVersionKey").asText(null)),
                parsePositiveLong(root.path("modelKey").asText(null))
        );
        if (direct != null) {
            return direct;
        }

        final JsonNode dataNode = root.path("data");
        if (!dataNode.isMissingNode() && !dataNode.isNull()) {
            return firstPositive(
                    parsePositiveLong(dataNode.path("modelVersionKey").asText(null)),
                    parsePositiveLong(dataNode.path("modelKey").asText(null))
            );
        }
        return null;
    }

    private static Long firstPositive(final Long primary, final Long secondary) {
        return primary != null ? primary : secondary;
    }

    /**
     * 양의 정수 long 문자열을 파싱합니다.
     */
    private static Long parsePositiveLong(final String text) {
        final String normalized = normalize(text);
        if (normalized == null) {
            return null;
        }
        try {
            final long value = Long.parseLong(normalized);
            if (value <= 0L) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 문자열을 null-safe 하게 정규화합니다.
     */
    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
