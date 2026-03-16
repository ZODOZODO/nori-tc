package com.nori.tc.business.core.workflow.internal.support;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver.ParsedActionDataIndex;
import com.nori.tc.business.core.workflow.internal.support.BusinessActionDataIndexHybridResolver.ValueSpec;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfFieldDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfMessageDefinition;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfOutputType;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MDF 정의와 action_data_index를 결합해 최종 메시지를 조립하는 컴포넌트입니다.
 *
 * <p>
 * MDF field의 {@code var}(variablePath)를 key로 action_data_index.fields에서 값을 조회합니다.
 * output 타입에 따라 직렬화 방식이 결정됩니다.
 * <ul>
 *   <li>RAW_MESSAGE: template 문자열에 field 값을 치환해 rawMessage 생성</li>
 *   <li>KAFKA: field 값을 Map으로 수집해 Kafka data 블록으로 사용</li>
 * </ul>
 * </p>
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
     * <p>{@code action_data_index.mdfTemplateName}이 존재할 때만 MDF 조립을 시도합니다.</p>
     *
     * @param context    액션 실행 컨텍스트
     * @param targetType MDF 타겟 타입
     * @return MDF 조립 결과 (action_data_index 또는 MDF 정의가 없으면 empty)
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

        if (log.isDebugEnabled()) {
            log.debug("MDF message composed. eqpId={}, workflowKey={}, actionName={}, mdfMessageName={}, targetType={}, outputType={}, fieldCount={}",
                    context.record().eqpId(),
                    context.workflowEntry().workflowKey(),
                    context.workflowEntry().actionName(),
                    messageDefinition.name(),
                    targetType,
                    messageDefinition.outputType(),
                    resolvedFields.size());
        }

        return Optional.of(new MdfComposeResult(messageDefinition, resolvedFields));
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
     * MDF field 목록을 기준으로 field 값을 계산합니다.
     *
     * <p>
     * 각 field의 {@code var}(variablePath)를 key로 action_data_index.fields에서 ValueSpec을 조회합니다.
     * ValueSpec이 없고 field가 required이면 예외를 발생시킵니다.
     * </p>
     */
    private Map<String, String> resolveFieldValues(
            final MdfMessageDefinition messageDefinition,
            final ParsedActionDataIndex parsedActionDataIndex,
            final BusinessWorkflowActionContext context
    ) {
        final Map<String, String> resolved = new LinkedHashMap<>();
        for (MdfFieldDefinition field : messageDefinition.fields()) {
            // field.variablePath() (var)를 key로 action_data_index.fields에서 값을 조회합니다.
            final ValueSpec spec = parsedActionDataIndex.fields().get(field.variablePath());
            if (spec != null) {
                resolved.put(field.name(), actionDataIndexResolver.resolveFieldValue(field.name(), spec, context));
            } else if (field.required()) {
                throw new IllegalArgumentException(
                        "Required MDF field value is missing in action_data_index. "
                                + "message=" + messageDefinition.name()
                                + ", field=" + field.name()
                                + ", var=" + field.variablePath()
                );
            } else {
                log.warn("MDF field value is missing and replaced with empty string. "
                                + "message={}, field={}, var={}, workflowKey={}",
                        messageDefinition.name(),
                        field.name(),
                        field.variablePath(),
                        context.workflowEntry().workflowKey());
                resolved.put(field.name(), "");
            }
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
     * MDF 조립 결과 모델입니다.
     *
     * <p>
     * output이 RAW_MESSAGE이면 {@link #rawMessage()}로 조립된 문자열을 반환합니다.
     * output이 KAFKA이면 {@link #kafkaDataBlock()}으로 Kafka data 블록 Map을 반환합니다.
     * </p>
     */
    public record MdfComposeResult(
            MdfMessageDefinition messageDefinition,
            Map<String, String> fieldValues
    ) {

        public MdfComposeResult {
            Objects.requireNonNull(messageDefinition, "messageDefinition is null");
            fieldValues = fieldValues == null ? Map.of() : Map.copyOf(fieldValues);
        }

        /**
         * RAW_MESSAGE output용 조립 문자열을 반환합니다.
         *
         * @throws IllegalStateException output이 RAW_MESSAGE가 아닌 경우
         */
        public String rawMessage() {
            if (messageDefinition.outputType() != MdfOutputType.RAW_MESSAGE) {
                throw new IllegalStateException(
                        "rawMessage() is only valid for RAW_MESSAGE output. actual=" + messageDefinition.outputType()
                );
            }
            return renderTemplate(messageDefinition.template(), fieldValues);
        }

        /**
         * KAFKA output용 data 블록 Map을 반환합니다.
         *
         * <p>이 Map은 Kafka 메시지의 {@code data} 블록에 그대로 사용됩니다.</p>
         *
         * @throws IllegalStateException output이 KAFKA가 아닌 경우
         */
        public Map<String, Object> kafkaDataBlock() {
            if (messageDefinition.outputType() != MdfOutputType.KAFKA) {
                throw new IllegalStateException(
                        "kafkaDataBlock() is only valid for KAFKA output. actual=" + messageDefinition.outputType()
                );
            }
            return Map.copyOf(fieldValues);
        }
    }
}
