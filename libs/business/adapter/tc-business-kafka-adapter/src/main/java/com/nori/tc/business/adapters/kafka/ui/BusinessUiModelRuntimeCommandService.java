package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.core.ui.BusinessUiTaskErrorCode;
import com.nori.tc.business.core.workflow.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.common.ui.task.pipeline.UiTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI 이벤트 기반 model runtime 제어 서비스입니다.
 *
 * <p>지원 시나리오:
 * - EQP_CREATE / EQP_UPDATE: eqpId -> modelKey 바인딩 갱신
 * - EQP_UPDATE_JARFILE: eqpId에 연결된 modelKey runtime 재조립</p>
 */
@Service
public class BusinessUiModelRuntimeCommandService {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiModelRuntimeCommandService.class);

    /**
     * "modelKey=123" 형태를 파싱하기 위한 정규식입니다.
     */
    private static final Pattern MODEL_KEY_KV_PATTERN = Pattern.compile("(?i)\\bmodelKey\\s*=\\s*([0-9]+)\\b");

    private final BusinessModelRuntimeMutationPort runtimeMutationPort;
    private final BusinessWorkflowPluginRuntimeMutationPort pluginRuntimeMutationPort;
    private final ObjectMapper objectMapper;

    /**
     * 서비스 의존성을 주입받습니다.
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
     * EQP_CREATE / EQP_UPDATE를 처리합니다.
     *
     * <p>uiMessage에서 modelKey를 추출하여 eqp 바인딩을 원자적으로 갱신합니다.</p>
     *
     * @param request UI 요청 메시지
     * @return 처리 결과
     */
    public UiTaskResult handleEqpCreateOrUpdate(final KafkaUiTaskMessage request) {
        final String eqpId = request.data().eqpId();
        final String traceId = request.metadata().traceId();
        final String eventType = request.metadata().eventType();

        final Long modelKey = resolveModelKey(request.data().uiMessage());
        if (modelKey == null) {
            log.warn("UI {} failed: modelKey not found. eqpId={}, traceId={}", eventType, eqpId, traceId);
            return UiTaskResult.fail(
                    BusinessUiTaskErrorCode.MODEL_KEY_REQUIRED,
                    "uiMessage에서 modelKey를 찾을 수 없습니다."
            );
        }

        try {
            runtimeMutationPort.updateEqpBinding(eqpId, modelKey);
        } catch (Exception ex) {
            log.error("UI {} failed during binding update. eqpId={}, traceId={}, modelKey={}",
                    eventType,
                    eqpId,
                    traceId,
                    modelKey,
                    ex);
            return UiTaskResult.fail(
                    BusinessUiTaskErrorCode.MODEL_RUNTIME_UPDATE_FAILED,
                    "eqpId-modelKey 바인딩 갱신 중 예외가 발생했습니다."
            );
        }

        log.info("UI {} success. eqpId={}, traceId={}, modelKey={}", eventType, eqpId, traceId, modelKey);
        return UiTaskResult.pass();
    }

    /**
     * EQP_UPDATE_JARFILE를 처리합니다.
     *
     * <p>uiMessage에 modelKey가 있으면 해당 modelKey를 사용하고,
     * 없으면 현재 eqpId 바인딩에서 modelKey를 조회하여 runtime을 재조립합니다.</p>
     *
     * @param request UI 요청 메시지
     * @return 처리 결과
     */
    public UiTaskResult handleEqpUpdateJarfile(final KafkaUiTaskMessage request) {
        final String eqpId = request.data().eqpId();
        final String traceId = request.metadata().traceId();

        final Long modelKeyFromMessage = resolveModelKey(request.data().uiMessage());
        final Long modelKey = modelKeyFromMessage != null
                ? modelKeyFromMessage
                : runtimeMutationPort.findModelKeyByEqpId(eqpId).orElse(null);
        if (modelKey == null) {
            log.warn("UI EQP_UPDATE_JARFILE failed: model binding not found. eqpId={}, traceId={}", eqpId, traceId);
            return UiTaskResult.fail(
                    BusinessUiTaskErrorCode.MODEL_BINDING_NOT_FOUND,
                    "eqpId에 연결된 modelKey를 찾을 수 없습니다."
            );
        }

        try {
            runtimeMutationPort.reloadModelRuntime(modelKey);
        } catch (Exception ex) {
            log.error("UI EQP_UPDATE_JARFILE failed during runtime reload. eqpId={}, traceId={}, modelKey={}",
                    eqpId,
                    traceId,
                    modelKey,
                    ex);
            return UiTaskResult.fail(
                    BusinessUiTaskErrorCode.MODEL_RUNTIME_UPDATE_FAILED,
                    "model runtime 재조립 중 예외가 발생했습니다."
            );
        }

        /*
         * model runtime 리로드가 성공한 뒤 plugin runtime 스왑을 수행합니다.
         * 실패 시에는 기존 plugin runtime을 유지하고 FAIL REP를 반환합니다.
         */
        try {
            pluginRuntimeMutationPort.reloadByEqpId(eqpId);
        } catch (Exception ex) {
            log.error("UI EQP_UPDATE_JARFILE failed during plugin runtime reload. eqpId={}, traceId={}, modelKey={}",
                    eqpId,
                    traceId,
                    modelKey,
                    ex);
            return UiTaskResult.fail(
                    BusinessUiTaskErrorCode.WORKFLOW_PLUGIN_RELOAD_FAILED,
                    "workflow plugin runtime 리로드 중 예외가 발생했습니다."
            );
        }

        log.info("UI EQP_UPDATE_JARFILE success. eqpId={}, traceId={}, modelKey={}", eqpId, traceId, modelKey);
        return UiTaskResult.pass();
    }

    /**
     * uiMessage에서 modelKey를 추출합니다.
     *
     * <p>지원 형식:
     * 1) JSON 객체: {"modelKey":123}
     * 2) JSON 중첩: {"data":{"modelKey":123}}
     * 3) 숫자 문자열: "123"
     * 4) key=value 문자열: "modelKey=123"</p>
     *
     * @param uiMessage UI 메시지 본문
     * @return modelKey(없으면 null)
     */
    Long resolveModelKey(final String uiMessage) {
        final String normalized = normalize(uiMessage);
        if (normalized == null) {
            return null;
        }

        /*
         * 먼저 JSON으로 해석을 시도합니다.
         * 운영 중 UI payload가 JSON으로 확장될 가능성이 높기 때문에 우선순위를 높게 둡니다.
         */
        try {
            final JsonNode root = objectMapper.readTree(normalized);
            final Long fromJson = extractModelKeyFromJson(root);
            if (fromJson != null) {
                if (log.isDebugEnabled()) {
                    log.debug("modelKey resolved from JSON. modelKey={}, uiMessage={}", fromJson, normalized);
                }
                return fromJson;
            }
        } catch (Exception ignored) {
            // JSON이 아니면 아래 plain text 경로로 계속 시도합니다.
        }

        final Long plainNumber = parsePositiveLong(normalized);
        if (plainNumber != null) {
            if (log.isDebugEnabled()) {
                log.debug("modelKey resolved from plain number. modelKey={}", plainNumber);
            }
            return plainNumber;
        }

        final Matcher matcher = MODEL_KEY_KV_PATTERN.matcher(normalized);
        if (matcher.find()) {
            final Long fromKv = parsePositiveLong(matcher.group(1));
            if (fromKv != null) {
                if (log.isDebugEnabled()) {
                    log.debug("modelKey resolved from key-value text. modelKey={}, text={}", fromKv, normalized);
                }
                return fromKv;
            }
        }

        return null;
    }

    private Long extractModelKeyFromJson(final JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        final Long direct = parsePositiveLong(root.path("modelKey").asText(null));
        if (direct != null) {
            return direct;
        }

        final JsonNode dataNode = root.path("data");
        if (!dataNode.isMissingNode() && !dataNode.isNull()) {
            final Long nested = parsePositiveLong(dataNode.path("modelKey").asText(null));
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

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


