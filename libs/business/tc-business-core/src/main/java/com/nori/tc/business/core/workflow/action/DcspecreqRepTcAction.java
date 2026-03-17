package com.nori.tc.business.core.workflow.action;

import com.nori.tc.business.core.datacoll.DatacollStatePort;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.annotation.TcAction;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractMesActionExecutor;
import com.nori.tc.business.core.workflow.internal.support.BusinessWorkflowCommandSupport;
import com.nori.tc.business.domain.datacoll.DatacollState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DCSPECREQ_REP 이벤트를 처리하는 MES 액션 실행기입니다.
 *
 * <p>MES로부터 DCSPECREQ_REP 이벤트를 수신하면 {@code data.dcspecValue}에서 수집 대상 항목 목록을 추출하고,
 * 초기 {@link DatacollState}를 생성하여 Redis에 저장합니다.</p>
 *
 * <p>저장된 DatacollState는 이후 {@code COLLECT_DCDATA} action이 실행될 때 참조되어
 * collectionState가 누적되고, {@code DATACOLL} action 시점에 최종값이 채워집니다.</p>
 *
 * <p>동일 eqpId + correlationId 조합의 Redis key가 이미 존재하면 WARN 로그를 남기고 덮어씁니다.
 * (예: 이전 lot이 비정상 종료되어 Redis가 정리되지 않은 경우)</p>
 *
 * <p>Redis Key 포맷: {@code tc:business:core:datacoll:{eqpId}:{correlationId}}</p>
 */
@Component
public class DcspecreqRepTcAction extends AbstractMesActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(DcspecreqRepTcAction.class);

    private final DatacollStatePort datacollStatePort;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param datacollStatePort DATACOLL 수집 상태 저장소 포트
     */
    public DcspecreqRepTcAction(final DatacollStatePort datacollStatePort) {
        this.datacollStatePort = Objects.requireNonNull(datacollStatePort, "datacollStatePort is null");
    }

    /**
     * DCSPECREQ_REP 이벤트를 처리합니다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>eqpId, correlationId 추출</li>
     *   <li>{@code data.dcspecValue}(수집 대상 항목 목록) 추출</li>
     *   <li>동일 key가 Redis에 이미 존재하면 WARN 후 덮어쓰기</li>
     *   <li>DatacollState 초기화 후 Redis 저장 (dcspecValue key 목록은 그대로, value는 모두 "", collectionState는 빈 Map)</li>
     * </ol>
     * </p>
     *
     * @param context 액션 실행 컨텍스트
     */
    @TcAction("DCSPECREQ_REP")
    public void handleDcspecreqRep(final BusinessWorkflowActionContext context) {
        final String eqpId = context.record().eqpId();
        final String correlationId = BusinessWorkflowCommandSupport.resolveCorrelationId(context);

        // correlationId가 없으면 lot 식별이 불가하므로 저장 불가
        if (correlationId == null) {
            log.warn("DCSPECREQ_REP 수신 시 correlationId가 누락되었습니다. eqpId={}, messageName={}",
                    eqpId, context.record().messageName());
            return;
        }

        // data.dcspecValue 추출 (수집 대상 항목 목록)
        final Map<String, String> dcspecValue = extractDcspecValue(context.messageVariables());

        if (dcspecValue == null || dcspecValue.isEmpty()) {
            log.warn("DCSPECREQ_REP 수신 시 dcspecValue가 비어 있습니다. eqpId={}, correlationId={}",
                    eqpId, correlationId);
        }

        // 동일 key가 이미 Redis에 존재하면 WARN 로그 후 덮어쓰기
        // (이전 lot 처리 중 DATACOLL 없이 중단된 경우 등)
        datacollStatePort.findByKey(eqpId, correlationId).ifPresent(existing ->
                log.warn("DCSPECREQ_REP 수신 시 동일 key의 DatacollState가 이미 존재합니다. 덮어씁니다. eqpId={}, correlationId={}",
                        eqpId, correlationId)
        );

        // DatacollState 초기화: dcspecValue key 목록을 보존하고 value는 모두 "", collectionState는 빈 Map
        final DatacollState state = DatacollState.initFrom(dcspecValue);
        datacollStatePort.save(eqpId, correlationId, state);

        log.info("DCSPECREQ_REP 처리 완료. eqpId={}, correlationId={}, dcspecValueKeys={}",
                eqpId, correlationId,
                dcspecValue != null ? dcspecValue.keySet() : "[]");
    }

    /**
     * messageVariables에서 {@code data.dcspecValue}를 추출하여 {@code Map<String, String>}으로 반환합니다.
     *
     * <p>메시지 구조:
     * <pre>
     * {
     *   "data": {
     *     "dcspecValue": { "Temperature": "", "Humidity": "" }
     *   }
     * }
     * </pre>
     * </p>
     *
     * <p>value가 null인 항목은 빈 문자열({@code ""})로 변환됩니다.
     * 경로가 없거나 타입 불일치 시 null을 반환합니다.</p>
     *
     * @param messageVariables 메시지 변수 맵
     * @return 추출된 dcspecValue Map. 경로가 없거나 타입 불일치 시 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractDcspecValue(final Map<String, Object> messageVariables) {
        if (messageVariables == null) {
            return null;
        }

        // data 블록 추출
        final Object dataBlock = messageVariables.get("data");
        if (!(dataBlock instanceof Map<?, ?> dataMap)) {
            return null;
        }

        // dcspecValue 블록 추출
        final Object dcspecValueBlock = ((Map<String, Object>) dataMap).get("dcspecValue");
        if (!(dcspecValueBlock instanceof Map<?, ?> rawMap)) {
            return null;
        }

        // Map<String, String>으로 변환 (value가 null이면 "")
        final Map<String, String> result = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : rawMap.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            final String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result.put(key, value);
        }
        return result;
    }
}
