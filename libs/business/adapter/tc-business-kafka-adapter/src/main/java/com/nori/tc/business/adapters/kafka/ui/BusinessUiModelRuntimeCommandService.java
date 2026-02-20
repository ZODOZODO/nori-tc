package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.tc.business.core.logging.BusinessLogContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.core.ui.BusinessUiTaskErrorCode;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI 紐낅졊 ?대깽?몃줈 model runtime??媛깆떊?섎뒗 ?쒕퉬?ㅼ엯?덈떎.
 *
 * <p>吏???쒕굹由ъ삤:</p>
 * <p>- EQP_CREATE / EQP_UPDATE: eqpId -> modelKey 諛붿씤??媛깆떊</p>
 * <p>- EQP_DELETE: eqpId 諛붿씤???쒓굅 + plugin runtime ?쒓굅</p>
 * <p>- EQP_UPDATE_JARFILE: model runtime 由щ줈??+ workflow plugin runtime 由щ줈??/p>
 */
@Service
public class BusinessUiModelRuntimeCommandService {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiModelRuntimeCommandService.class);

    /**
     * "modelKey=123" ?뺥깭 ?띿뒪?몃? ?뚯떛?섍린 ?꾪븳 ?⑦꽩?낅땲??
     */
    private static final Pattern MODEL_KEY_KV_PATTERN = Pattern.compile("(?i)\\bmodelKey\\s*=\\s*([0-9]+)\\b");

    private final BusinessModelRuntimeMutationPort runtimeMutationPort;
    private final BusinessWorkflowPluginRuntimeMutationPort pluginRuntimeMutationPort;
    private final ObjectMapper objectMapper;

    /**
     * ?쒕퉬???섏〈?깆쓣 珥덇린?뷀빀?덈떎.
     *
     * @param runtimeMutationPort model runtime 蹂寃??ы듃
     * @param pluginRuntimeMutationPort workflow plugin runtime 蹂寃??ы듃
     * @param objectMapper JSON ?뚯꽌
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
     * EQP_CREATE / EQP_UPDATE瑜?泥섎━?⑸땲??
     *
     * <p>uiMessage?먯꽌 modelKey瑜?異붿텧??eqp 諛붿씤?⑹쓣 媛깆떊?⑸땲??</p>
     *
     * @param request UI ?붿껌 硫붿떆吏
     * @return 泥섎━ 寃곌낵
     */
    public KafkaTaskResult handleEqpCreateOrUpdate(final KafkaUiTaskMessage request) {
        final String eqpId = normalize(request.data().eqpId());
        final String traceId = normalize(request.metadata().traceId());
        final String eventType = request.metadata().eventType();
        if (eqpId == null) {
            return KafkaTaskResult.fail(
                    BusinessUiTaskErrorCode.EQP_ID_REQUIRED,
                    "eqpId???꾩닔?낅땲??"
            );
        }

        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(eqpId, traceId)) {
            log.info("UI {} request received. eqpId={}, traceId={}", eventType, eqpId, traceId);

            final Long modelKey = resolveModelKey(request.data().uiMessage());
            if (modelKey == null) {
                log.warn("UI {} failed: modelKey not found. eqpId={}, traceId={}", eventType, eqpId, traceId);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_KEY_REQUIRED,
                        "uiMessage?먯꽌 modelKey瑜?李얠쓣 ???놁뒿?덈떎."
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
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_RUNTIME_UPDATE_FAILED,
                        "eqpId-modelKey 諛붿씤??媛깆떊???ㅽ뙣?덉뒿?덈떎."
                );
            }

            log.info("UI {} success. eqpId={}, traceId={}, modelKey={}", eventType, eqpId, traceId, modelKey);
            if (log.isDebugEnabled()) {
                log.debug("UI {} binding update detail. eqpId={}, modelKey={}", eventType, eqpId, modelKey);
            }
            return KafkaTaskResult.pass();
        }
    }

    /**
     * EQP_UPDATE_JARFILE瑜?泥섎━?⑸땲??
     *
     * <p>uiMessage??modelKey瑜??곗꽑 ?ъ슜?섍퀬, ?놁쑝硫?eqpId 諛붿씤?⑹뿉??議고쉶??     * modelKey濡?runtime??由щ줈?쒗빀?덈떎.</p>
     *
     * @param request UI ?붿껌 硫붿떆吏
     * @return 泥섎━ 寃곌낵
     */
    public KafkaTaskResult handleEqpUpdateJarfile(final KafkaUiTaskMessage request) {
        final String eqpId = normalize(request.data().eqpId());
        final String traceId = normalize(request.metadata().traceId());
        if (eqpId == null) {
            return KafkaTaskResult.fail(
                    BusinessUiTaskErrorCode.EQP_ID_REQUIRED,
                    "eqpId???꾩닔?낅땲??"
            );
        }

        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(eqpId, traceId)) {
            log.info("UI EQP_UPDATE_JARFILE request received. eqpId={}, traceId={}", eqpId, traceId);

            final Long modelKeyFromMessage = resolveModelKey(request.data().uiMessage());
            final Long modelKey = modelKeyFromMessage != null
                    ? modelKeyFromMessage
                    : runtimeMutationPort.findModelKeyByEqpId(eqpId).orElse(null);
            if (modelKey == null) {
                log.warn("UI EQP_UPDATE_JARFILE failed: model binding not found. eqpId={}, traceId={}", eqpId, traceId);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_BINDING_NOT_FOUND,
                        "eqpId??留ㅽ븨??modelKey瑜?李얠쓣 ???놁뒿?덈떎."
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
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_RUNTIME_UPDATE_FAILED,
                        "model runtime 由щ줈?쒖뿉 ?ㅽ뙣?덉뒿?덈떎."
                );
            }

            /*
             * model runtime 由щ줈???깃났 ??plugin runtime 由щ줈?쒕? ?쒖감 ?섑뻾?⑸땲??
             * ???④퀎媛 ?ㅽ뙣?섎㈃ ?꾩껜 ?붿껌???ㅽ뙣濡?泥섎━?⑸땲??
             */
            try {
                pluginRuntimeMutationPort.reloadByEqpId(eqpId);
            } catch (Exception ex) {
                log.error("UI EQP_UPDATE_JARFILE failed during plugin runtime reload. eqpId={}, traceId={}, modelKey={}",
                        eqpId,
                        traceId,
                        modelKey,
                        ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.WORKFLOW_PLUGIN_RELOAD_FAILED,
                        "workflow plugin runtime 由щ줈?쒖뿉 ?ㅽ뙣?덉뒿?덈떎."
                );
            }

            log.info("UI EQP_UPDATE_JARFILE success. eqpId={}, traceId={}, modelKey={}", eqpId, traceId, modelKey);
            if (log.isDebugEnabled()) {
                log.debug("UI EQP_UPDATE_JARFILE detail. eqpId={}, modelKey={}", eqpId, modelKey);
            }
            return KafkaTaskResult.pass();
        }
    }

    /**
     * EQP_DELETE瑜?泥섎━?⑸땲??
     *
     * <p>泥섎━ ?뺤콉:</p>
     * <p>1) eqpId -> modelKey 諛붿씤?⑹쓣 癒쇱? ?쒓굅?⑸땲??</p>
     * <p>2) 諛붿씤???쒓굅 ???대떦 eqp??workflow plugin runtime???쒓굅?⑸땲??</p>
     * <p>3) model runtime 罹먯떆??諛붿씤???쒓굅 ?④퀎?먯꽌 "李몄“ 0媛????뚮쭔 ?먮룞 ?쒓굅?⑸땲??</p>
     *
     * @param request UI ?붿껌 硫붿떆吏
     * @return 泥섎━ 寃곌낵
     */
    public KafkaTaskResult handleEqpDelete(final KafkaUiTaskMessage request) {
        final String eqpId = normalize(request.data().eqpId());
        final String traceId = normalize(request.metadata().traceId());
        if (eqpId == null) {
            return KafkaTaskResult.fail(
                    BusinessUiTaskErrorCode.EQP_ID_REQUIRED,
                    "eqpId???꾩닔?낅땲??"
            );
        }

        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(eqpId, traceId)) {
            log.info("UI EQP_DELETE request received. eqpId={}, traceId={}", eqpId, traceId);

            final Optional<Long> removedModelKey;
            try {
                removedModelKey = runtimeMutationPort.removeEqpBinding(eqpId);
            } catch (Exception ex) {
                log.error("UI EQP_DELETE failed during binding remove. eqpId={}, traceId={}", eqpId, traceId, ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_BINDING_DELETE_FAILED,
                        "eqpId-modelKey 諛붿씤????젣???ㅽ뙣?덉뒿?덈떎."
                );
            }

            if (removedModelKey.isEmpty()) {
                log.warn("UI EQP_DELETE failed: model binding not found. eqpId={}, traceId={}", eqpId, traceId);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.MODEL_BINDING_NOT_FOUND,
                        "??젣??eqpId-modelKey 諛붿씤?⑹씠 議댁옱?섏? ?딆뒿?덈떎."
                );
            }

            try {
                pluginRuntimeMutationPort.removeByEqpId(eqpId);
            } catch (Exception ex) {
                log.error("UI EQP_DELETE failed during plugin runtime remove. eqpId={}, traceId={}", eqpId, traceId, ex);
                return KafkaTaskResult.fail(
                        BusinessUiTaskErrorCode.WORKFLOW_PLUGIN_REMOVE_FAILED,
                        "workflow plugin runtime ?쒓굅???ㅽ뙣?덉뒿?덈떎."
                );
            }

            log.info("UI EQP_DELETE success. eqpId={}, traceId={}, removedModelKey={}",
                    eqpId,
                    traceId,
                    removedModelKey.orElse(null));
            if (log.isDebugEnabled()) {
                log.debug("UI EQP_DELETE detail. eqpId={}, removedModelKey={}", eqpId, removedModelKey.orElse(null));
            }
            return KafkaTaskResult.pass();
        }
    }

    /**
     * uiMessage?먯꽌 modelKey瑜?異붿텧?⑸땲??
     *
     * <p>吏???щ㎎:</p>
     * <p>1) JSON: {"modelKey":123}</p>
     * <p>2) JSON 以묒꺽: {"data":{"modelKey":123}}</p>
     * <p>3) ?レ옄 臾몄옄?? "123"</p>
     * <p>4) key=value 臾몄옄?? "modelKey=123"</p>
     *
     * @param uiMessage UI 硫붿떆吏 蹂몃Ц
     * @return modelKey(?놁쑝硫?null)
     */
    Long resolveModelKey(final String uiMessage) {
        final String normalized = normalize(uiMessage);
        if (normalized == null) {
            return null;
        }

        /*
         * 1李? JSON ?뚯떛?쇰줈 modelKey瑜??먯깋?⑸땲??
         * JSON ?뚯떛 ?ㅽ뙣 ???덉쇅瑜??꾪뙆?섏? ?딄퀬 ?ㅼ쓬 ?щ㎎ ?뚯떛?쇰줈 吏꾪뻾?⑸땲??
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
            // JSON???꾨땲硫?plain text 寃쎈줈濡?怨꾩냽 泥섎━?⑸땲??
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

    /**
     * JSON 猷⑦듃/以묒꺽 寃쎈줈?먯꽌 modelKey瑜?異붿텧?⑸땲??
     *
     * @param root JSON 猷⑦듃 ?몃뱶
     * @return modelKey(?놁쑝硫?null)
     */
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

    /**
     * ?묒닔 long 臾몄옄?댁쓣 ?뚯떛?⑸땲??
     *
     * @param text ?뚯떛 ???臾몄옄??     * @return ?묒닔 long 媛??ㅽ뙣 ??null)
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
     * 臾몄옄?댁쓣 null-safe?섍쾶 ?뺢퇋?뷀빀?덈떎.
     *
     * @param value ?먮낯 臾몄옄??     * @return trim 寃곌낵(鍮?臾몄옄?댁씠硫?null)
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





