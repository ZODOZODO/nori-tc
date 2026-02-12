package com.nori.tc.comm.adapters.db;

import com.nori.tc.comm.gateway.context.EquipmentStatePersistencePort;
import com.nori.tc.db.core.eqp.store.TcEqpStateHistStore;
import com.nori.tc.db.core.eqp.store.TcEqpStateStore;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpState;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpStateHist;
import com.nori.tc.db.domain.common.eqp.ControlState;
import com.nori.tc.db.domain.common.eqp.EqpState;
import com.nori.tc.db.domain.common.eqp.EqpStateType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 설비 상태/이력 DB 영속화 어댑터입니다.
 *
 * <p>설계 합의사항:</p>
 * <p>- tc_eqp_state: eqp별 1행 upsert(현재 상태)</p>
 * <p>- tc_eqp_state_hist: 전이 이력 append</p>
 * <p>- reason_detail: "traceId=&lt;값&gt;;message=&lt;값&gt;" 규약 문자열 저장</p>
 */
@Service
public class GatewayEquipmentStatePersistenceService implements EquipmentStatePersistencePort {

    private static final Logger log = LoggerFactory.getLogger(GatewayEquipmentStatePersistenceService.class);

    private final TcEqpStore eqpStore;
    private final TcEqpStateStore stateStore;
    private final TcEqpStateHistStore stateHistStore;

    /**
     * 상태 저장에 필요한 DB Store 포트를 주입받습니다.
     */
    public GatewayEquipmentStatePersistenceService(
            final TcEqpStore eqpStore,
            final TcEqpStateStore stateStore,
            final TcEqpStateHistStore stateHistStore
    ) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore is null");
        this.stateHistStore = Objects.requireNonNull(stateHistStore, "stateHistStore is null");
    }

    /**
     * CREATE/UPDATE 요청 이력을 기록합니다.
     *
     * <p>현재 상태를 강제 변경하지 않고 OPER 이력만 append합니다.</p>
     */
    @Override
    public void recordCreateOrUpdate(
            final String eqpId,
            final String traceId,
            final String eventType,
            final String detailMessage
    ) {
        final TcEqp eqp = requireEqp(eqpId);
        final Optional<TcEqpState> currentState = stateStore.findByEqpKey(eqp.eqpKey());

        appendHist(
                eqp.eqpKey(),
                EqpStateType.OPER,
                currentState.map(state -> state.eqpState() == null ? null : state.eqpState().name()).orElse(null),
                "REGISTERED",
                normalizeReasonCode(eventType, "EQP_UPDATE"),
                buildReasonDetail(traceId, detailMessage)
        );

        if (log.isDebugEnabled()) {
            log.debug("State history recorded for create/update. eqpId={}, eventType={}, traceId={}",
                    eqpId, eventType, traceId);
        }
    }

    /**
     * START 요청 결과를 현재 상태와 이력에 반영합니다.
     */
    @Override
    public void recordStart(final String eqpId, final String traceId, final String detailMessage) {
        final TcEqp eqp = requireEqp(eqpId);
        final Optional<TcEqpState> currentState = stateStore.findByEqpKey(eqp.eqpKey());

        upsertState(
                eqp.eqpKey(),
                ControlState.REMOTE,
                EqpState.IDLE,
                "EQP_START",
                buildReasonDetail(traceId, detailMessage)
        );

        appendHist(
                eqp.eqpKey(),
                EqpStateType.CONN,
                currentState.map(state -> state.controlState() == null ? null : state.controlState().name()).orElse(null),
                "STARTED",
                "EQP_START",
                buildReasonDetail(traceId, detailMessage)
        );

        log.info("State persisted for start. eqpId={}, traceId={}", eqpId, traceId);
    }

    /**
     * END 요청 결과를 현재 상태와 이력에 반영합니다.
     */
    @Override
    public void recordEnd(final String eqpId, final String traceId, final String detailMessage) {
        final TcEqp eqp = requireEqp(eqpId);
        final Optional<TcEqpState> currentState = stateStore.findByEqpKey(eqp.eqpKey());

        upsertState(
                eqp.eqpKey(),
                ControlState.OFFLINE,
                EqpState.DOWN,
                "EQP_END",
                buildReasonDetail(traceId, detailMessage)
        );

        appendHist(
                eqp.eqpKey(),
                EqpStateType.CONN,
                currentState.map(state -> state.controlState() == null ? null : state.controlState().name()).orElse(null),
                "ENDED",
                "EQP_END",
                buildReasonDetail(traceId, detailMessage)
        );

        log.info("State persisted for end. eqpId={}, traceId={}", eqpId, traceId);
    }

    /**
     * DELETE 요청 결과를 현재 상태와 이력에 반영합니다.
     */
    @Override
    public void recordDelete(final String eqpId, final String traceId, final String detailMessage) {
        final TcEqp eqp = requireEqp(eqpId);
        final Optional<TcEqpState> currentState = stateStore.findByEqpKey(eqp.eqpKey());

        upsertState(
                eqp.eqpKey(),
                ControlState.OFFLINE,
                EqpState.DOWN,
                "EQP_DELETE",
                buildReasonDetail(traceId, detailMessage)
        );

        appendHist(
                eqp.eqpKey(),
                EqpStateType.OPER,
                currentState.map(state -> state.eqpState() == null ? null : state.eqpState().name()).orElse(null),
                "DELETED",
                "EQP_DELETE",
                buildReasonDetail(traceId, detailMessage)
        );

        log.info("State persisted for delete. eqpId={}, traceId={}", eqpId, traceId);
    }

    /**
     * tc_eqp_state를 upsert합니다.
     */
    private void upsertState(
            final long eqpKey,
            final ControlState controlState,
            final EqpState eqpState,
            final String reasonCode,
            final String reasonDetail
    ) {
        stateStore.upsert(new UpsertTcEqpState(
                eqpKey,
                controlState,
                eqpState,
                OffsetDateTime.now(),
                reasonCode,
                reasonDetail,
                OffsetDateTime.now()
        ));
    }

    /**
     * tc_eqp_state_hist에 전이 이력을 append합니다.
     */
    private void appendHist(
            final long eqpKey,
            final EqpStateType stateType,
            final String fromState,
            final String toState,
            final String reasonCode,
            final String reasonDetail
    ) {
        stateHistStore.append(new UpsertTcEqpStateHist(
                eqpKey,
                stateType,
                fromState,
                toState,
                OffsetDateTime.now(),
                reasonCode,
                reasonDetail
        ));
    }

    /**
     * eqpId로 tc_eqp 기본 행을 조회합니다.
     */
    private TcEqp requireEqp(final String eqpId) {
        return eqpStore.findByEqpId(eqpId).orElseThrow(
                () -> new IllegalStateException("Equipment not found for state persistence. eqpId=" + eqpId)
        );
    }

    /**
     * reason_code 값이 비어있을 경우 기본 코드를 보정합니다.
     */
    private String normalizeReasonCode(final String reasonCode, final String defaultCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return defaultCode;
        }
        return reasonCode.trim();
    }

    /**
     * reason_detail 규약 문자열을 생성합니다.
     *
     * <p>합의 규약: traceId=<값>;message=<값></p>
     */
    private String buildReasonDetail(final String traceId, final String detailMessage) {
        final String normalizedTraceId = (traceId == null || traceId.isBlank()) ? "UNKNOWN" : traceId.trim();
        final String normalizedMessage = (detailMessage == null || detailMessage.isBlank())
                ? "n/a"
                : detailMessage.trim();
        return "traceId=" + normalizedTraceId + ";message=" + normalizedMessage;
    }
}

