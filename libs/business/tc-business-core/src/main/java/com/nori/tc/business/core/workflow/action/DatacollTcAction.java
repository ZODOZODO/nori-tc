package com.nori.tc.business.core.workflow.action;

import com.nori.tc.business.core.datacoll.DatacollStatePort;
import com.nori.tc.business.core.datacoll.DcopCollectionEngine;
import com.nori.tc.business.core.datacoll.DcopItemPort;
import com.nori.tc.business.core.messaging.BusinessMesCommandMessage;
import com.nori.tc.business.core.messaging.BusinessMesCommandPublishPort;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.annotation.TcAction;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractMesActionExecutor;
import com.nori.tc.business.core.workflow.internal.support.BusinessWorkflowCommandSupport;
import com.nori.tc.business.domain.datacoll.DatacollState;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * DATACOLL workflow action을 처리하는 MES 액션 실행기입니다.
 *
 * <p>Redis에서 수집 상태({@link DatacollState})를 읽어 최종값을 결정하고,
 * MES에 DATACOLL 메시지를 Kafka로 보고한 뒤 Redis key를 정리합니다.</p>
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>eqpId, correlationId 추출</li>
 *   <li>Redis에서 DatacollState 조회 (없으면 WARN 후 중단)</li>
 *   <li>modelVersionKey 기준 DCOP Item 목록 조회</li>
 *   <li>{@link DcopCollectionEngine#resolveFinalValues}로 Collection Rule / Calculation Rule 최종값 결정</li>
 *   <li>dcspecValue 기준으로 최종값 채우기 (매핑 없는 항목은 "")</li>
 *   <li>DATACOLL 메시지 조립 후 MES로 Kafka 발행</li>
 *   <li>Redis key 즉시 삭제 (삭제 실패 시 ERROR 로그만 남김 - TTL로 최종 정리)</li>
 * </ol>
 * </p>
 *
 * <p>실패 정책:
 * <ul>
 *   <li>Redis에 DatacollState 없음: WARN 로그 후 DATACOLL 발행 건너뜀 (lot 상태 오류로 간주)</li>
 *   <li>Redis 삭제 실패: ERROR 로그. TTL로 최종 정리됨 (누수 방지)</li>
 * </ul>
 * </p>
 */
@Component
public class DatacollTcAction extends AbstractMesActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(DatacollTcAction.class);

    /** DATACOLL Kafka 메시지의 eventType 값 */
    private static final String EVENT_TYPE_DATACOLL = "DATACOLL";

    private final DatacollStatePort datacollStatePort;
    private final DcopCollectionEngine dcopCollectionEngine;
    private final DcopItemPort dcopItemPort;
    private final BusinessMesCommandPublishPort mesCommandPublishPort;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param datacollStatePort    DATACOLL 수집 상태 저장소 포트
     * @param dcopCollectionEngine DCOP Item 최종값 결정 엔진
     * @param dcopItemPort         DCOP Item 조회 포트
     * @param mesCommandPublishPort MES 명령 발행 포트
     */
    public DatacollTcAction(
            final DatacollStatePort datacollStatePort,
            final DcopCollectionEngine dcopCollectionEngine,
            final DcopItemPort dcopItemPort,
            final BusinessMesCommandPublishPort mesCommandPublishPort
    ) {
        this.datacollStatePort = Objects.requireNonNull(datacollStatePort, "datacollStatePort is null");
        this.dcopCollectionEngine = Objects.requireNonNull(dcopCollectionEngine, "dcopCollectionEngine is null");
        this.dcopItemPort = Objects.requireNonNull(dcopItemPort, "dcopItemPort is null");
        this.mesCommandPublishPort = Objects.requireNonNull(mesCommandPublishPort, "mesCommandPublishPort is null");
    }

    /**
     * DATACOLL action을 실행합니다.
     *
     * <p>Redis 수집 상태를 최종값으로 변환하여 MES에 보고하고, Redis key를 삭제합니다.</p>
     *
     * @param context 액션 실행 컨텍스트
     * @throws Exception MES Kafka 발행 실패 시
     */
    @TcAction("DATACOLL")
    public void reportDatacoll(final BusinessWorkflowActionContext context) throws Exception {
        final String eqpId = context.record().eqpId();
        final String correlationId = BusinessWorkflowCommandSupport.resolveCorrelationId(context);

        // correlationId가 없으면 lot 식별이 불가하므로 중단
        if (correlationId == null) {
            log.warn("correlationId 누락. DATACOLL을 건너뜁니다. eqpId={}", eqpId);
            return;
        }

        // 1. Redis에서 DatacollState 조회
        final Optional<DatacollState> stateOptional = datacollStatePort.findByKey(eqpId, correlationId);
        if (stateOptional.isEmpty()) {
            // DCSPECREQ_REP를 받지 않은 lot이거나 TTL 만료된 경우
            log.warn("DatacollState not found for eqpId={}, correlationId={}. DCSPECREQ_REP를 받지 않은 상태로 간주하여 DATACOLL을 건너뜁니다.",
                    eqpId, correlationId);
            return;
        }

        final DatacollState state = stateOptional.get();
        final long modelVersionKey = context.modelRuntime().modelVersionKey();

        // 2. modelVersionKey 기준 DCOP Item 전체 조회
        final List<TcModelDcopItem> dcopItems = dcopItemPort.findAllByModelVersionKey(modelVersionKey);

        // 3. Collection Rule / Calculation Rule 적용하여 최종값 결정
        final Map<String, String> finalValues = dcopCollectionEngine.resolveFinalValues(state, dcopItems);

        // 4. dcspecValue 기준으로 최종값 채우기
        // - dcopItemName이 finalValues에 있으면 해당 값 사용
        // - 매핑 없는 항목은 "" 그대로 (설계 명세에 따른 처리)
        final Map<String, String> filledDcspecValue = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : state.getDcspecValue().entrySet()) {
            final String dcopItemName = entry.getKey();
            final String finalValue = finalValues.getOrDefault(dcopItemName, "");
            filledDcspecValue.put(dcopItemName, finalValue);
        }

        // 5. DATACOLL 메시지 데이터 조립
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("eqpId", eqpId);
        // interfaceType: 모델의 프로토콜 타입을 MES에 알리기 위해 포함
        data.put("interfaceType", context.modelRuntime().protocolType().name());
        data.put("dcspecValue", filledDcspecValue);

        // 6. MES로 DATACOLL Kafka 발행
        final BusinessMesCommandMessage message = new BusinessMesCommandMessage(
                EVENT_TYPE_DATACOLL,
                correlationId,
                eqpId,
                BusinessWorkflowCommandSupport.resolveTraceId(context),
                data
        );
        mesCommandPublishPort.publish(message);

        log.info("DATACOLL reported to MES. eqpId={}, correlationId={}, itemCount={}",
                eqpId, correlationId, filledDcspecValue.size());

        // 7. Redis key 즉시 삭제 (발행 완료 후 정리)
        // 삭제 실패 시에도 TTL이 자동 만료되므로 예외를 전파하지 않음
        try {
            datacollStatePort.delete(eqpId, correlationId);
        } catch (Exception ex) {
            log.error("DATACOLL 보고 완료 후 Redis key 삭제 실패. TTL로 최종 정리됩니다. eqpId={}, correlationId={}",
                    eqpId, correlationId, ex);
        }
    }
}
