package com.nori.tc.business.core.workflow.internal.support;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver.ParsedActionDataIndex;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver.ValueSpec;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfFieldDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfMessageDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
     * 타겟(EQP/MES)에 맞는 MDF 메시지를 조립합니다.
     *
     * <p>새 정책에서는 {@code action_data_index.mdfTemplateName}이 존재할 때만 MDF 조립을 시도합니다.</p>
     *
     * @param context 액션 실행 컨텍스트
     * @param targetType MDF 타겟 타입
     * @return MDF 조립 결과({@code action_data_index} 또는 MDF 정의가 없으면 empty)
     */
    public Optional<MdfComposeResult> compose(
            final BusinessWorkflowActionContext context,
            final MdfTargetType targetType
    ) {
        Objects.requireNonNull(context, "context is null");
        Objects.requireNonNull(targetType, "targetType is null");

        final MdfRuntimeDefinition runtimeDefinition = context.modelRuntime().mdfRuntimeDefinition();
        if (runtimeDefinition.isEmpty()) {
            return Optional.empty();
        }

        final ParsedActionDataIndex parsedActionDataIndex =
                actionDataIndexResolver.parse(context.workflowEntry().actionDataIndex());
        if (parsedActionDataIndex.isEmpty()) {
            return Optional.empty();
        }

        final MdfMessageDefinition messageDefinition = selectMessageDefinition(
                runtimeDefinition,
                parsedActionDataIndex,
                targetType,
                context
        );
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
                    context.workflowEntry().actionName(),
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
    private MdfMessageDefinition selectMessageDefinition(
            final MdfRuntimeDefinition runtimeDefinition,
            final ParsedActionDataIndex parsedActionDataIndex,
            final MdfTargetType targetType,
            final BusinessWorkflowActionContext context
    ) {
        if (parsedActionDataIndex.mdfTemplateName() == null) {
            throw new IllegalArgumentException(
                    "mdfTemplateName is required when action_data_index is provided. workflowKey="
                            + context.workflowEntry().workflowKey()
            );
        }

        final MdfMessageDefinition messageDefinition = runtimeDefinition.findMessage(parsedActionDataIndex.mdfTemplateName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "MDF message not found. mdfTemplateName=" + parsedActionDataIndex.mdfTemplateName()
                ));

        if (messageDefinition.targetType() != targetType) {
            throw new IllegalArgumentException(
                    "MDF message target mismatch. mdfTemplateName=" + messageDefinition.name()
                            + ", expectedTarget=" + targetType
                            + ", actualTarget=" + messageDefinition.targetType()
            );
        }
        return messageDefinition;
    }

    /**
     * 메시지 정의와 action_data_index를 합쳐 필드 값을 계산합니다.
     *
     * <p>우선순위는 {@code action_data_index.fields[field]} → MDF {@code <field>} 정의 순서입니다.</p>
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
        fieldNames.addAll(parsedActionDataIndex.fields().keySet());

        final Map<String, String> resolved = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            final ValueSpec overrideSpec = parsedActionDataIndex.fields().get(fieldName);
            if (overrideSpec != null) {
                resolved.put(fieldName, actionDataIndexResolver.resolveFieldValue(fieldName, overrideSpec, context));
                continue;
            }

            final Optional<MdfFieldDefinition> fieldDefinition = messageDefinition.findField(fieldName);
            if (fieldDefinition.isPresent()) {
                resolved.put(
                        fieldName,
                        actionDataIndexResolver.resolveMdfFieldValue(fieldName, fieldDefinition.orElseThrow(), context)
                );
                continue;
            }

            resolved.put(fieldName, "");
        }
        return resolved;
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
