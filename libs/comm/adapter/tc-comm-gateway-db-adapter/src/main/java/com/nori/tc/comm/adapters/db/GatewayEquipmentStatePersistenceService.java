package com.nori.tc.comm.adapters.db;

import com.nori.tc.comm.gateway.context.port.EquipmentStatePersistenceCommand;
import com.nori.tc.comm.gateway.context.port.EquipmentStatePersistencePort;
import com.nori.tc.db.core.eqp.store.TcEqpStateHistStore;
import com.nori.tc.db.core.eqp.store.TcEqpStateStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpState;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpStateHist;
import com.nori.tc.db.domain.common.eqp.ControlState;
import com.nori.tc.db.domain.common.eqp.EqpState;
import com.nori.tc.db.domain.common.eqp.EqpStateType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;

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

    private final TcEqpStateStore stateStore;
    private final TcEqpStateHistStore stateHistStore;

    /**
     * 상태 저장에 필요한 DB Store 포트를 주입받습니다.
     */
    public GatewayEquipmentStatePersistenceService(
            final TcEqpStateStore stateStore,
            final TcEqpStateHistStore stateHistStore
    ) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore is null");
        this.stateHistStore = Objects.requireNonNull(stateHistStore, "stateHistStore is null");
    }

    /**
     * CREATE/UPDATE 요청 이력을 커맨드 기반 입력으로 기록합니다.
     *
     * <p>DB 접근 최소화 정책에 따라 eqpId -> eqpKey 재조회는 수행하지 않고,
     * 호출부가 전달한 eqpKey를 그대로 사용합니다.</p>
     *
     * <p>기본 정책은 선행 state 조회 없이 이력 append만 수행합니다.</p>
     *
     * @param command 상태/이력 영속화 커맨드
     */
    @Override
    public void recordCreateOrUpdate(final EquipmentStatePersistenceCommand command) {
        final EquipmentStatePersistenceCommand normalized = requireCommand(command).normalizeReasonCodeIfBlank();

        appendHist(
                normalized.eqpKey(),
                EqpStateType.OPER,
                normalized.fromEqpState(),
                normalized.toEqpState() == null ? "REGISTERED" : normalized.toEqpState(),
                normalizeReasonCode(normalized.reasonCode(), "EQP_UPDATE"),
                buildReasonDetail(normalized.traceId(), normalized.detailMessage())
        );

        if (log.isDebugEnabled()) {
            log.debug("State history recorded for create/update (command). eqpId={}, eqpKey={}, eventType={}, traceId={}",
                    normalized.eqpId(),
                    normalized.eqpKey(),
                    normalized.eventType(),
                    normalized.traceId());
        }
    }

    /**
     * CREATE/UPDATE 요청 이력을 기록합니다.
     *
     * <p>신규 구현에서는 eqpKey 기반 command 오버로드를 사용해야 하므로,
     * 이 메서드는 호출부 누락을 빠르게 발견하기 위한 보호용 예외를 발생시킵니다.</p>
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
        throw legacyApiNotSupported("recordCreateOrUpdate", eqpId, eventType);
    }

    /**
     * START 요청 결과를 커맨드 기반 입력으로 현재 상태와 이력에 반영합니다.
     *
     * <p>선행 state 조회 없이 커맨드에 포함된 fromControlState(선택)를 그대로 사용합니다.</p>
     *
     * @param command 상태/이력 영속화 커맨드
     */
    @Override
    public void recordStart(final EquipmentStatePersistenceCommand command) {
        final EquipmentStatePersistenceCommand normalized = requireCommand(command).normalizeReasonCodeIfBlank();

        upsertState(
                normalized.eqpKey(),
                parseControlState(normalized.toControlState(), ControlState.REMOTE),
                parseEqpState(normalized.toEqpState(), EqpState.IDLE),
                normalizeReasonCode(normalized.reasonCode(), "EQP_START"),
                buildReasonDetail(normalized.traceId(), normalized.detailMessage())
        );

        appendHist(
                normalized.eqpKey(),
                EqpStateType.CONN,
                normalized.fromControlState(),
                "STARTED",
                "EQP_START",
                buildReasonDetail(normalized.traceId(), normalized.detailMessage())
        );

        log.info("State persisted for start (command). eqpId={}, eqpKey={}, traceId={}",
                normalized.eqpId(),
                normalized.eqpKey(),
                normalized.traceId());
    }

    /**
     * START 요청 결과를 현재 상태와 이력에 반영합니다.
     */
    @Override
    public void recordStart(final String eqpId, final String traceId, final String detailMessage) {
        throw legacyApiNotSupported("recordStart", eqpId, "EQP_START");
    }

    /**
     * END 요청 결과를 커맨드 기반 입력으로 현재 상태와 이력에 반영합니다.
     *
     * @param command 상태/이력 영속화 커맨드
     */
    @Override
    public void recordEnd(final EquipmentStatePersistenceCommand command) {
        final EquipmentStatePersistenceCommand normalized = requireCommand(command).normalizeReasonCodeIfBlank();

        upsertState(
                normalized.eqpKey(),
                parseControlState(normalized.toControlState(), ControlState.OFFLINE),
                parseEqpState(normalized.toEqpState(), EqpState.DOWN),
                normalizeReasonCode(normalized.reasonCode(), "EQP_END"),
                buildReasonDetail(normalized.traceId(), normalized.detailMessage())
        );

        appendHist(
                normalized.eqpKey(),
                EqpStateType.CONN,
                normalized.fromControlState(),
                "ENDED",
                "EQP_END",
                buildReasonDetail(normalized.traceId(), normalized.detailMessage())
        );

        log.info("State persisted for end (command). eqpId={}, eqpKey={}, traceId={}",
                normalized.eqpId(),
                normalized.eqpKey(),
                normalized.traceId());
    }

    /**
     * END 요청 결과를 현재 상태와 이력에 반영합니다.
     */
    @Override
    public void recordEnd(final String eqpId, final String traceId, final String detailMessage) {
        throw legacyApiNotSupported("recordEnd", eqpId, "EQP_END");
    }

    /**
     * DELETE 요청 결과를 커맨드 기반 입력으로 현재 상태와 이력에 반영합니다.
     *
     * @param command 상태/이력 영속화 커맨드
     */
    @Override
    public void recordDelete(final EquipmentStatePersistenceCommand command) {
        final EquipmentStatePersistenceCommand normalized = requireCommand(command).normalizeReasonCodeIfBlank();

        upsertState(
                normalized.eqpKey(),
                parseControlState(normalized.toControlState(), ControlState.OFFLINE),
                parseEqpState(normalized.toEqpState(), EqpState.DOWN),
                normalizeReasonCode(normalized.reasonCode(), "EQP_DELETE"),
                buildReasonDetail(normalized.traceId(), normalized.detailMessage())
        );

        appendHist(
                normalized.eqpKey(),
                EqpStateType.OPER,
                normalized.fromEqpState(),
                "DELETED",
                "EQP_DELETE",
                buildReasonDetail(normalized.traceId(), normalized.detailMessage())
        );

        log.info("State persisted for delete (command). eqpId={}, eqpKey={}, traceId={}",
                normalized.eqpId(),
                normalized.eqpKey(),
                normalized.traceId());
    }

    /**
     * DELETE 요청 결과를 현재 상태와 이력에 반영합니다.
     */
    @Override
    public void recordDelete(final String eqpId, final String traceId, final String detailMessage) {
        throw legacyApiNotSupported("recordDelete", eqpId, "EQP_DELETE");
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

    /**
     * 신규 command 기반 API 입력을 검증합니다.
     *
     * @param command 상태/이력 영속화 커맨드
     * @return 검증된 커맨드
     */
    private EquipmentStatePersistenceCommand requireCommand(final EquipmentStatePersistenceCommand command) {
        return Objects.requireNonNull(command, "command is null");
    }

    /**
     * 문자열 controlState를 DB enum으로 파싱합니다.
     *
     * <p>값이 비어 있거나 파싱 실패 시 기본값을 사용합니다.
     * 이 정책은 런타임 hot path의 상태 영속화를 실패로 전파하지 않기 위한 보수적 처리입니다.</p>
     *
     * @param value 원본 문자열
     * @param defaultValue 기본값
     * @return 파싱된 enum 또는 기본값
     */
    private ControlState parseControlState(final String value, final ControlState defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return ControlState.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            log.warn("Invalid controlState text for state persistence. value={}, fallback={}", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 문자열 eqpState를 DB enum으로 파싱합니다.
     *
     * @param value 원본 문자열
     * @param defaultValue 기본값
     * @return 파싱된 enum 또는 기본값
     */
    private EqpState parseEqpState(final String value, final EqpState defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return EqpState.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            log.warn("Invalid eqpState text for state persistence. value={}, fallback={}", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 구형 문자열 기반 API 호출을 차단하는 예외를 생성합니다.
     *
     * <p>eqpKey가 없는 호출은 DB 재조회를 유발하므로, DB 접근 최소화 전환 이후에는 허용하지 않습니다.</p>
     *
     * @param apiName 호출된 API 이름
     * @param eqpId 설비 ID
     * @param eventType 이벤트 타입
     * @return 항상 throw할 예외 객체
     */
    private IllegalStateException legacyApiNotSupported(
            final String apiName,
            final String eqpId,
            final String eventType
    ) {
        log.error("Legacy state persistence API was invoked after command-based migration. api={}, eqpId={}, eventType={}",
                apiName,
                eqpId,
                eventType);
        return new IllegalStateException("Legacy state persistence API is not supported. Use EquipmentStatePersistenceCommand.");
    }
}
