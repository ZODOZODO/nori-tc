package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.db.domain.common.model.ProtocolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link BusinessWorkflowMatcher} 기본 구현체입니다.
 *
 * <p>매칭 순서:</p>
 * <p>1) messageName 기준 후보 추출</p>
 * <p>2) HSMS인 경우 eventId/transactionId 추가 매칭</p>
 * <p>3) workflow_filter(JSON Rule) 평가</p>
 */
@Component
public class BusinessWorkflowMatcherImpl implements BusinessWorkflowMatcher {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowMatcherImpl.class);

    private final BusinessWorkflowPayloadExtractor payloadExtractor;
    private final BusinessWorkflowFilterEvaluator filterEvaluator;

    /**
     * 매처 의존성을 주입받습니다.
     */
    public BusinessWorkflowMatcherImpl(
            final BusinessWorkflowPayloadExtractor payloadExtractor,
            final BusinessWorkflowFilterEvaluator filterEvaluator
    ) {
        this.payloadExtractor = Objects.requireNonNull(payloadExtractor, "payloadExtractor is null");
        this.filterEvaluator = Objects.requireNonNull(filterEvaluator, "filterEvaluator is null");
    }

    @Override
    public BusinessWorkflowMatchResult match(final BusinessInboundRecord record, final TcModelRuntime modelRuntime) {
        Objects.requireNonNull(record, "record is null");
        Objects.requireNonNull(modelRuntime, "modelRuntime is null");

        final Map<String, Object> messageVariables = payloadExtractor.extractMessageVariables(record.payload());
        final Map<String, Object> contextVariables = payloadExtractor.buildContextVariables(record);
        final BusinessWorkflowFilterContext filterContext = new BusinessWorkflowFilterContext(
                record,
                messageVariables,
                contextVariables
        );

        final List<WorkflowRuntimeEntry> candidates = resolveCandidates(record, modelRuntime);
        if (candidates.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Workflow candidate not found by message key. eqpId={}, messageName={}, protocolType={}",
                        record.eqpId(),
                        record.messageName(),
                        modelRuntime.protocolType());
            }
            return new BusinessWorkflowMatchResult(List.of(), filterContext);
        }

        final List<WorkflowRuntimeEntry> matched = new ArrayList<>();
        for (WorkflowRuntimeEntry entry : candidates) {
            final boolean passed;
            try {
                passed = filterEvaluator.evaluate(entry, filterContext);
            } catch (BusinessWorkflowFilterEvaluationException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BusinessWorkflowFilterEvaluationException(
                        "workflow_filter evaluation failed. workflowKey=" + entry.workflowKey(),
                        ex
                );
            }

            if (passed) {
                matched.add(entry);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Workflow match completed. eqpId={}, messageName={}, candidateCount={}, matchedCount={}",
                    record.eqpId(),
                    record.messageName(),
                    candidates.size(),
                    matched.size());
        }

        return new BusinessWorkflowMatchResult(List.copyOf(matched), filterContext);
    }

    private List<WorkflowRuntimeEntry> resolveCandidates(
            final BusinessInboundRecord record,
            final TcModelRuntime modelRuntime
    ) {
        final List<WorkflowRuntimeEntry> byMessageName = modelRuntime.findWorkflowsByMessageName(record.messageName());
        if (byMessageName.isEmpty()) {
            return List.of();
        }

        if (modelRuntime.protocolType() != ProtocolType.HSMS) {
            return byMessageName;
        }

        final String eventId = payloadExtractor.extractEventId(record.payload());
        final String transactionId = payloadExtractor.extractTransactionId(record.payload());

        final List<WorkflowRuntimeEntry> matched = new ArrayList<>();
        for (WorkflowRuntimeEntry entry : byMessageName) {
            if (matchesSecsKey(entry, eventId, transactionId)) {
                matched.add(entry);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("SECS workflow key matching completed. eqpId={}, messageName={}, eventId={}, transactionId={}, beforeCount={}, afterCount={}",
                    record.eqpId(),
                    record.messageName(),
                    eventId,
                    transactionId,
                    byMessageName.size(),
                    matched.size());
        }

        return List.copyOf(matched);
    }

    /**
     * SECS workflow 매칭 규칙입니다.
     *
     * <p>workflow row에서 eventId/transactionId가 지정된 경우에만 값 일치를 강제합니다.
     * 둘 중 하나라도 row에서 비어 있으면 해당 키는 와일드카드처럼 동작합니다.</p>
     */
    private static boolean matchesSecsKey(
            final WorkflowRuntimeEntry entry,
            final String eventId,
            final String transactionId
    ) {
        if (entry.eventId() != null && !entry.eventId().equals(eventId)) {
            return false;
        }
        if (entry.transactionId() != null && !entry.transactionId().equals(transactionId)) {
            return false;
        }
        return true;
    }
}


