package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.logging.BusinessLogContext;
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
 * UI 이벤트를 기반으로 model runtime 캐시를 제어하는 서비스입니다.
 *
 * <p>지원 시나리오:</p>
 * <p>1) EQP_CREATE / EQP_UPDATE: eqpId -> modelVersionKey 바인딩 갱신</p>
 * <p>2) EQP_UPDATE_JARFILE: model runtime 리로드 + workflow plugin runtime 리로드</p>
 * <p>3) EQP_DELETE: eqpId 바인딩 제거 + workflow plugin runtime 제거</p>
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
    private final BusinessWorkflowPluginRuntimeMutationPort pluginRuntimeMutationPort;
    private final ObjectMapper objectMapper;

    /**
     * 필수 의존성을 주입합니다.
     */
    public BusinessUiModelRuntimeCommandService(
            final BusinessModelRuntimeMutationPort runtimeMutationPort,
            final BusinessWorkflowPluginRuntimeMutationPort pluginRuntimeMutationPort,
            final ObjectMapper objectMapper
    ) {
        this.runtimeMutationPort = Objects.requireNonNull(runtimeMutationPort, "runtimeMutationPort is null");
        this.pluginRuntimeMutationPort = Objects.requireNonNull(pluginRuntimeMutationPort, "pluginRuntimeMutationPort is null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
    }

    /**
     * EQP_CREATE / EQP_UPDATE 이벤트를 처리합니다.
     *
     * <p>uiMessage에서 modelVersionKey를 추출하여 eqp 바인딩을 갱신합니다.</p>
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

            log.info("UI {} success. eqpId={}, traceId={}, modelVersionKey={}",
                    eventType, eqpId, traceId, modelVersionKey);
            if (log.isDebugEnabled()) {
                log.debug("UI {} binding update detail. eqpId={}, modelVersionKey={}",
                        eventType, eqpId, modelVersionKey);
            }
            return KafkaTaskResult.pass();
        }
    }

    /**
     * EQP_UPDATE_JARFILE 이벤트를 처리합니다.
     *
     * <p>우선순위:</p>
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
