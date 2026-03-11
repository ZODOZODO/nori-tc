package com.nori.tc.business.core.workflow.internal.matching;

import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterContext;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowFilterEvaluationException;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatchResult;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatcher;
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
 * 인바운드 메시지에 대해 실행 가능한 워크플로우 목록을 결정하는 매처 구현체입니다.
 *
 * <p>매칭 절차:</p>
 * <p>1) payload에서 message/context 변수를 추출</p>
 * <p>2) messageName 기준 후보 워크플로우 조회</p>
 * <p>3) HSMS 프로토콜이면 eventId/transactionId 키로 2차 필터링</p>
 * <p>4) workflow_filter(JSON Rule) 평가로 최종 매칭 확정</p>
 */
@Component
public class BusinessWorkflowMatcherImpl implements BusinessWorkflowMatcher {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowMatcherImpl.class);

    private final BusinessWorkflowPayloadExtractor payloadExtractor;
    private final BusinessWorkflowFilterEvaluator filterEvaluator;

    /**
     * 필수 협력 객체를 주입받아 매처를 생성합니다.
     *
     * @param payloadExtractor payload 분석기
     * @param filterEvaluator workflow_filter 평가기
     */
    public BusinessWorkflowMatcherImpl(
            final BusinessWorkflowPayloadExtractor payloadExtractor,
            final BusinessWorkflowFilterEvaluator filterEvaluator
    ) {
        this.payloadExtractor = Objects.requireNonNull(payloadExtractor, "payloadExtractor is null");
        this.filterEvaluator = Objects.requireNonNull(filterEvaluator, "filterEvaluator is null");
    }

    /**
     * 인바운드 레코드와 모델 런타임 정보로 워크플로우 매칭을 수행합니다.
     *
     * @param record 수신 원본 레코드
     * @param modelRuntime 모델 런타임 정보
     * @return 필터 컨텍스트와 최종 매칭 워크플로우 목록
     */
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

    /**
     * 1차 후보 워크플로우를 messageName 및 프로토콜 키 기준으로 정리합니다.
     *
     * <p>HSMS가 아닌 경우 messageName 일치 결과를 그대로 반환하고,
     * HSMS인 경우 eventId/transactionId 조건을 추가로 검사합니다.</p>
     *
     * @param record 수신 원본 레코드
     * @param modelRuntime 모델 런타임 정보
     * @return 후보 워크플로우 목록
     */
    private List<WorkflowRuntimeEntry> resolveCandidates(
            final BusinessInboundRecord record,
            final TcModelRuntime modelRuntime
    ) {
        final List<WorkflowRuntimeEntry> byMessageName = modelRuntime.findWorkflowsByMessageName(record.messageName());
        if (byMessageName.isEmpty()) {
            return List.of();
        }

        if (modelRuntime.protocolType() != ProtocolType.SECS) {
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
     * HSMS 워크플로우 키(eventId, transactionId) 일치 여부를 판단합니다.
     *
     * <p>워크플로우 쪽 값이 null이면 해당 조건은 와일드카드로 간주합니다.</p>
     *
     * @param entry 검사 대상 워크플로우 엔트리
     * @param eventId 수신 payload에서 추출한 eventId
     * @param transactionId 수신 payload에서 추출한 transactionId
     * @return 키 조건 일치 여부
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



