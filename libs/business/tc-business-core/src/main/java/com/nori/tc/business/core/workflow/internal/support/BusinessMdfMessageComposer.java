package com.nori.tc.business.core.workflow.internal.support;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver.ParsedActionDataIndex;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver.TransformSpec;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver.ValueSpec;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfFieldDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfMessageDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MDF 정의와 action_data_index를 결합해 최종 메시지를 조립하는 컴포넌트입니다.
 */
@Component
public class BusinessMdfMessageComposer {

    private static final Logger log = LoggerFactory.getLogger(BusinessMdfMessageComposer.class);

    private static final Pattern TEMPLATE_FIELD_PATTERN = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");

    private final BusinessActionDataIndexHybridResolver actionDataIndexResolver;

    /**
     * 하이브리드 해석기를 주입받습니다.
     */
    public BusinessMdfMessageComposer(final BusinessActionDataIndexHybridResolver actionDataIndexResolver) {
        this.actionDataIndexResolver = Objects.requireNonNull(actionDataIndexResolver, "actionDataIndexResolver is null");
    }

    /**
     * 타겟(EQP/MES)과 액션명에 맞는 MDF 메시지를 조립합니다.
     *
     * @param context 액션 실행 컨텍스트
     * @param targetType MDF 타겟 타입
     * @param actionName 액션명
     * @return MDF 조립 결과(해당 정의가 없으면 empty)
     */
    public Optional<MdfComposeResult> compose(
            final BusinessWorkflowActionContext context,
            final MdfTargetType targetType,
            final String actionName
    ) {
        Objects.requireNonNull(context, "context is null");
        Objects.requireNonNull(targetType, "targetType is null");
        if (actionName == null || actionName.isBlank()) {
            throw new IllegalArgumentException("actionName is required");
        }

        final MdfRuntimeDefinition runtimeDefinition = context.modelRuntime().mdfRuntimeDefinition();
        if (runtimeDefinition.isEmpty()) {
            return Optional.empty();
        }

        final ParsedActionDataIndex parsedActionDataIndex =
                actionDataIndexResolver.parse(context.workflowEntry().actionDataIndex());

        final Optional<MdfMessageDefinition> selectedMessage = selectMessageDefinition(
                runtimeDefinition,
                parsedActionDataIndex,
                targetType,
                actionName.trim(),
                context
        );
        if (selectedMessage.isEmpty()) {
            return Optional.empty();
        }

        final MdfMessageDefinition messageDefinition = selectedMessage.orElseThrow();
        final Map<String, String> resolvedFields = resolveFieldValues(
                messageDefinition,
                parsedActionDataIndex,
                context
        );

        final String renderedMessage = renderTemplate(messageDefinition.template(), resolvedFields);
        if (log.isDebugEnabled()) {
            log.debug("MDF message composed. eqpId={}, workflowKey={}, actionName={}, mdfMessageName={}, targetType={}, fieldCount={}",
                    context.record().eqpId(),
                    context.workflowEntry().workflowKey(),
                    actionName,
                    messageDefinition.name(),
                    targetType,
                    resolvedFields.size());
        }

        return Optional.of(new MdfComposeResult(
                messageDefinition,
                renderedMessage,
                Map.copyOf(resolvedFields)
        ));
    }

    /**
     * 액션 컨텍스트에서 사용할 MDF 메시지 정의를 선택합니다.
     */
    private Optional<MdfMessageDefinition> selectMessageDefinition(
            final MdfRuntimeDefinition runtimeDefinition,
            final ParsedActionDataIndex parsedActionDataIndex,
            final MdfTargetType targetType,
            final String actionName,
            final BusinessWorkflowActionContext context
    ) {
        if (parsedActionDataIndex.messageName() != null) {
            final MdfMessageDefinition byName = runtimeDefinition.findMessage(parsedActionDataIndex.messageName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "MDF message not found. messageName=" + parsedActionDataIndex.messageName()
                    ));

            if (byName.targetType() != targetType) {
                throw new IllegalArgumentException(
                        "MDF message target mismatch. messageName=" + byName.name()
                                + ", expectedTarget=" + targetType
                                + ", actualTarget=" + byName.targetType()
                );
            }
            return Optional.of(byName);
        }

        final List<MdfMessageDefinition> candidates = runtimeDefinition.findByActionAndTarget(actionName, targetType);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        if (candidates.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple MDF messages matched. actionName=" + actionName
                            + ", targetType=" + targetType
                            + ", workflowKey=" + context.workflowEntry().workflowKey()
                            + ", candidates=" + candidates.stream().map(MdfMessageDefinition::name).toList()
            );
        }
        return Optional.of(candidates.getFirst());
    }

    /**
     * 메시지 정의와 action_data_index를 합쳐 필드 값을 계산합니다.
     */
    private Map<String, String> resolveFieldValues(
            final MdfMessageDefinition messageDefinition,
            final ParsedActionDataIndex parsedActionDataIndex,
            final BusinessWorkflowActionContext context
    ) {
        final Set<String> fieldNames = new LinkedHashSet<>();
        fieldNames.addAll(extractTemplatePlaceholders(messageDefinition.template()));
        for (MdfFieldDefinition fieldDefinition : messageDefinition.fields()) {
            fieldNames.add(fieldDefinition.name());
        }
        fieldNames.addAll(parsedActionDataIndex.fieldSpecs().keySet());

        final Map<String, String> resolved = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            final ValueSpec spec = chooseValueSpec(messageDefinition, parsedActionDataIndex, fieldName);
            final String value = actionDataIndexResolver.resolveFieldValue(fieldName, spec, context);
            resolved.put(fieldName, value);
        }
        return resolved;
    }

    /**
     * 필드별 value spec 우선순위를 결정합니다.
     *
     * <p>
     * 우선순위:
     * 1) action_data_index.fields[field]
     * 2) MDF field 정의
     * 3) 기본값(AUTO path=field, required=false)
     * </p>
     */
    private static ValueSpec chooseValueSpec(
            final MdfMessageDefinition messageDefinition,
            final ParsedActionDataIndex parsedActionDataIndex,
            final String fieldName
    ) {
        final ValueSpec overrideSpec = parsedActionDataIndex.fieldSpecs().get(fieldName);
        if (overrideSpec != null) {
            return overrideSpec;
        }

        final Optional<MdfFieldDefinition> fieldDefinition = messageDefinition.findField(fieldName);
        if (fieldDefinition.isPresent()) {
            final MdfFieldDefinition definition = fieldDefinition.orElseThrow();
            return new ValueSpec(
                    definition.variablePath(),
                    definition.sourceType(),
                    parseTransforms(definition.xformChain()),
                    definition.fixedValue(),
                    definition.required()
            );
        }

        return new ValueSpec(fieldName, MdfRuntimeDefinition.MdfSourceType.AUTO, List.of(), null, false);
    }

    /**
     * 문자열 xform 체인을 TransformSpec 목록으로 변환합니다.
     */
    private static List<TransformSpec> parseTransforms(final List<String> xformChain) {
        if (xformChain == null || xformChain.isEmpty()) {
            return List.of();
        }

        final List<TransformSpec> transforms = new ArrayList<>();
        for (String xform : xformChain) {
            transforms.add(TransformSpec.fromCompactText(xform));
        }
        return List.copyOf(transforms);
    }

    /**
     * 템플릿 문자열의 플레이스홀더를 치환합니다.
     */
    private static String renderTemplate(final String template, final Map<String, String> resolvedFields) {
        final Matcher matcher = TEMPLATE_FIELD_PATTERN.matcher(template);
        final StringBuffer sb = new StringBuffer(template.length() + 64);
        while (matcher.find()) {
            final String fieldName = matcher.group(1);
            final String replacement = resolvedFields.getOrDefault(fieldName, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 템플릿의 필드명을 추출합니다.
     */
    private static Set<String> extractTemplatePlaceholders(final String template) {
        final Set<String> fieldNames = new LinkedHashSet<>();
        final Matcher matcher = TEMPLATE_FIELD_PATTERN.matcher(template);
        while (matcher.find()) {
            final String fieldName = matcher.group(1);
            if (fieldName != null && !fieldName.isBlank()) {
                fieldNames.add(fieldName.trim());
            }
        }
        return fieldNames;
    }

    /**
     * MDF 조립 결과 모델입니다.
     */
    public record MdfComposeResult(
            MdfMessageDefinition messageDefinition,
            String renderedMessage,
            Map<String, String> fieldValues
    ) {

        public MdfComposeResult {
            Objects.requireNonNull(messageDefinition, "messageDefinition is null");
            if (renderedMessage == null || renderedMessage.isBlank()) {
                throw new IllegalArgumentException("renderedMessage is required");
            }
            fieldValues = fieldValues == null ? Map.of() : Map.copyOf(fieldValues);
        }

        /**
         * MES publish용 data 맵을 반환합니다.
         */
        public Map<String, Object> toMesData() {
            final Map<String, Object> data = new LinkedHashMap<>();
            data.put("mdfMessageName", messageDefinition.name());
            data.put("mdfTarget", messageDefinition.targetType().name());
            data.put("mdfRenderedMessage", renderedMessage);
            data.putAll(fieldValues);
            return Map.copyOf(data);
        }
    }
}
