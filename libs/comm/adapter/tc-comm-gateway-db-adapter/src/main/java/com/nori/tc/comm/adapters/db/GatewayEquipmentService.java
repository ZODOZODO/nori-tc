package com.nori.tc.comm.adapters.db;

import com.nori.tc.comm.gateway.db.ConnectionMode;
import com.nori.tc.comm.gateway.equipment.port.EquipmentInfoProvider;
import com.nori.tc.comm.gateway.context.model.EquipmentContextProfile;
import com.nori.tc.comm.gateway.context.port.EquipmentContextProfileProvider;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.store.TcEqpLogStore;
import com.nori.tc.db.core.eqp.store.TcEqpParamStore;
import com.nori.tc.db.core.eqp.store.TcEqpPortStatusStore;
import com.nori.tc.db.core.eqp.store.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.store.TcEqpStateStore;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.domain.eqp.TcEqpLog;
import com.nori.tc.db.domain.eqp.TcEqpParam;
import com.nori.tc.db.domain.eqp.TcEqpPortStatus;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import com.nori.tc.db.domain.eqp.TcEqpState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 게이트웨이 설비 조회용 DB 어댑터입니다.
 *
 * <p>역할:</p>
 * <p>- EquipmentInfoProvider: 통신 라우팅/연결 판단에 필요한 핵심 정보 조회</p>
 * <p>- EquipmentContextProfileProvider: EquipmentContextRegistry 초기화/갱신용 상세 프로파일 조회</p>
 */
@Service
public class GatewayEquipmentService implements EquipmentInfoProvider, EquipmentContextProfileProvider {

    private static final Logger log = LoggerFactory.getLogger(GatewayEquipmentService.class);

    /**
     * 페이지 단위 조회 시 사용하는 공통 limit입니다.
     * 큰 테이블을 한 번에 메모리로 올리지 않기 위해 고정합니다.
     */
    private static final int PAGE_LIMIT = PageRequest.defaultPage().limit();

    private final TcEqpStore eqpStore;
    private final KafkaShardOwnership shardOwnership;
    private final TcEqpHsmsStore hsmsStore;
    private final TcEqpSocketStore socketStore;
    private final TcEqpStateStore stateStore;
    private final TcEqpPortStatusStore portStatusStore;
    private final TcEqpLogStore logStore;
    private final TcEqpParamStore paramStore;

    /**
     * DB 조회에 필요한 Store 포트를 주입받습니다.
     */
    public GatewayEquipmentService(
            final TcEqpStore eqpStore,
            final KafkaShardOwnership shardOwnership,
            final TcEqpHsmsStore hsmsStore,
            final TcEqpSocketStore socketStore,
            final TcEqpStateStore stateStore,
            final TcEqpPortStatusStore portStatusStore,
            final TcEqpLogStore logStore,
            final TcEqpParamStore paramStore
    ) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        this.shardOwnership = Objects.requireNonNull(shardOwnership, "shardOwnership is null");
        this.hsmsStore = Objects.requireNonNull(hsmsStore, "hsmsStore is null");
        this.socketStore = Objects.requireNonNull(socketStore, "socketStore is null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore is null");
        this.portStatusStore = Objects.requireNonNull(portStatusStore, "portStatusStore is null");
        this.logStore = Objects.requireNonNull(logStore, "logStore is null");
        this.paramStore = Objects.requireNonNull(paramStore, "paramStore is null");
    }

    /**
     * 현재 Gateway 인스턴스가 소유한 partition의 활성(enabled) 설비 핵심 정보 목록을 조회합니다.
     *
     * <p>U7 변경 규칙:</p>
     * <p>- 전체 tc_eqp 테이블을 읽지 않고, {@code route_partition IN ownedPartitions AND enabled=true} 조건으로 조회합니다.</p>
     * <p>- 반환 대상은 "게이트웨이 런타임 기동 후보"이며, 비활성 설비는 포함하지 않습니다.</p>
     *
     * @return 현재 인스턴스 소유 partition의 활성 설비 핵심 정보 목록
     */
    @Override
    public List<GatewayEquipmentInfo> findAll() {
        final List<Integer> ownedPartitions = shardOwnership.ownedPartitions();
        if (ownedPartitions.isEmpty()) {
            log.info("설비 핵심 정보 로딩을 건너뜁니다. 사유=소유 partition 없음");
            return List.of();
        }

        log.info("소유 partition 기준 활성 설비 핵심 정보 로딩 시작. ownedPartitions={}", ownedPartitions);

        final List<GatewayEquipmentInfo> results = new ArrayList<>();
        int offset = 0;
        while (true) {
            final List<TcEqp> page = eqpStore.findAllByRoutePartitionsAndEnabled(
                    ownedPartitions,
                    true,
                    PageRequest.of(offset, PAGE_LIMIT)
            );
            if (page.isEmpty()) {
                break;
            }
            if (log.isDebugEnabled()) {
                log.debug("설비 핵심 정보 페이지 로딩 완료. offset={}, pageSize={}, ownedPartitions={}",
                        offset,
                        page.size(),
                        ownedPartitions);
            }
            for (TcEqp eqp : page) {
                results.add(toInfo(eqp));
            }
            if (page.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }

        log.info("소유 partition 기준 활성 설비 핵심 정보 로딩 완료. count={}, ownedPartitions={}", results.size(), ownedPartitions);
        return results;
    }

    /**
     * 단일 설비의 핵심 정보를 조회합니다.
     */
    @Override
    public Optional<GatewayEquipmentInfo> findById(final String equipmentId) {
        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(equipmentId)) {
            if (log.isDebugEnabled()) {
                log.debug("Loading equipment info by eqpId={}", equipmentId);
            }
            return eqpStore.findByEqpId(equipmentId).map(this::toInfo);
        }
    }

    /**
     * 현재 Gateway 인스턴스가 소유한 partition의 설비 컨텍스트 프로파일 목록을 조회합니다.
     *
     * <p>DB 접근 최소화 + EQP bean 중심 전환 이후에는 런타임 hot path에서 DB fallback을 제거하므로,
     * 부팅 시점에 owned partition의 설비를 enabled=true/false 구분 없이 모두 bean에 적재해야 합니다.</p>
     *
     * <p>실제 자동 런타임 기동 여부는 상위 부트스트랩에서 enabled 값으로 분기합니다.</p>
     *
     * @return 현재 인스턴스 소유 partition의 설비 컨텍스트 프로파일 목록(enabled 포함)
     */
    @Override
    public List<EquipmentContextProfile> findAllProfiles() {
        final List<Integer> ownedPartitions = shardOwnership.ownedPartitions();
        if (ownedPartitions.isEmpty()) {
            log.info("설비 컨텍스트 프로파일 로딩을 건너뜁니다. 사유=소유 partition 없음");
            return List.of();
        }

        log.info("소유 partition 기준 설비 컨텍스트 프로파일 로딩 시작(enabled=true/false 포함). ownedPartitions={}", ownedPartitions);

        final List<EquipmentContextProfile> results = new ArrayList<>();
        loadProfilesByEnabledFlag(results, ownedPartitions, true);
        loadProfilesByEnabledFlag(results, ownedPartitions, false);

        log.info("소유 partition 기준 설비 컨텍스트 프로파일 로딩 완료(enabled=true/false 포함). count={}, ownedPartitions={}",
                results.size(),
                ownedPartitions);
        return results;
    }

    /**
     * 특정 enabled 플래그 조건으로 설비 프로파일을 페이지 단위로 조회하여 결과 리스트에 추가합니다.
     *
     * <p>기존 DB 포트 계약({@code findAllByRoutePartitionsAndEnabled})을 유지하면서도
     * 부팅 시 disabled 설비까지 bean 적재 대상으로 포함하기 위해 true/false를 각각 조회합니다.</p>
     *
     * @param target 누적 결과 리스트
     * @param ownedPartitions 현재 Gateway 인스턴스 소유 파티션 목록
     * @param enabled 조회 대상 enabled 플래그
     */
    private void loadProfilesByEnabledFlag(
            final List<EquipmentContextProfile> target,
            final List<Integer> ownedPartitions,
            final boolean enabled
    ) {
        Objects.requireNonNull(target, "target is null");
        Objects.requireNonNull(ownedPartitions, "ownedPartitions is null");

        int offset = 0;
        while (true) {
            final List<TcEqp> page = eqpStore.findAllByRoutePartitionsAndEnabled(
                    ownedPartitions,
                    enabled,
                    PageRequest.of(offset, PAGE_LIMIT)
            );
            if (page.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("설비 컨텍스트 프로파일 페이지 로딩 종료. enabled={}, offset={}, reason=EMPTY_PAGE",
                            enabled, offset);
                }
                break;
            }
            if (log.isDebugEnabled()) {
                log.debug("설비 컨텍스트 프로파일 페이지 로딩 완료. enabled={}, offset={}, pageSize={}, ownedPartitions={}",
                        enabled,
                        offset,
                        page.size(),
                        ownedPartitions);
            }
            for (TcEqp eqp : page) {
                target.add(toProfile(eqp));
            }
            if (page.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }
    }

    /**
     * 단일 설비의 컨텍스트 프로파일을 조회합니다.
     */
    @Override
    public Optional<EquipmentContextProfile> findProfileById(final String eqpId) {
        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
            if (log.isDebugEnabled()) {
                log.debug("Loading equipment context profile by eqpId={}", eqpId);
            }
            return eqpStore.findByEqpId(eqpId).map(this::toProfile);
        }
    }

    /**
     * tc_eqp + tc_eqp_hsms/tc_eqp_socket를 조합해서 GatewayEquipmentInfo를 구성합니다.
     *
     * <p>U6 변경 규칙:</p>
     * <p>- 연결 모드는 하위 테이블(tc_eqp_hsms/tc_eqp_socket)에서 읽지 않고 {@code tc_eqp.comm_mode}를 사용합니다.</p>
     * <p>- 하위 테이블은 protocol별 상세 설정(예: socketType, hsms deviceId) 조회에만 사용합니다.</p>
     * <p>- routePartition은 후속 라우팅/소유권 단계에서 사용할 수 있도록 함께 적재합니다.</p>
     *
     * @param eqp tc_eqp 공통 설비 행
     * @return 게이트웨이 런타임 핵심 설비 정보
     */
    private GatewayEquipmentInfo toInfo(final TcEqp eqp) {
        if (eqp == null) {
            throw new IllegalArgumentException("eqp is null");
        }

        final Long eqpKey = eqp.eqpKey();
        if (eqpKey == null || eqpKey <= 0) {
            throw new IllegalStateException("Invalid eqpKey for eqpId=" + eqp.eqpId());
        }
        if (eqp.eqpId() == null || eqp.eqpId().isBlank()) {
            throw new IllegalStateException("Invalid eqpId for eqpKey=" + eqpKey);
        }

        final CommInterfaceType commInterfaceType = toCommInterfaceType(eqp.commInterface());
        final ConnectionMode connectionMode = resolveConnectionModeFromEqp(eqp);

        String socketType = null;
        Integer hsmsDeviceId = null;

        if (commInterfaceType == CommInterfaceType.SOCKET) {
            final TcEqpSocket socket = socketStore.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new IllegalStateException("Missing tc_eqp_socket for eqpId=" + eqp.eqpId()));
            socketType = socket.socketProtocolType();
        } else if (commInterfaceType == CommInterfaceType.HSMS) {
            final TcEqpHsms hsms = hsmsStore.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new IllegalStateException("Missing tc_eqp_hsms for eqpId=" + eqp.eqpId()));
            hsmsDeviceId = hsms.deviceId();
        }

        if (log.isDebugEnabled()) {
            log.debug("설비 핵심 정보 매핑 완료. eqpId={}, interface={}, commMode={}, routePartition={}, socketType={}, hsmsDeviceId={}",
                    eqp.eqpId(),
                    commInterfaceType,
                    connectionMode,
                    eqp.routePartition(),
                    socketType,
                    hsmsDeviceId);
        }

        return new GatewayEquipmentInfo(
                eqpKey,
                eqp.eqpId(),
                commInterfaceType,
                socketType,
                hsmsDeviceId,
                eqp.eqpIp(),
                eqp.eqpPort(),
                eqp.modelKey(),
                connectionMode,
                eqp.routePartition(),
                eqp.enabled()
        );
    }

    /**
     * tc_eqp 계열 테이블을 조합해서 EquipmentContextProfile을 구성합니다.
     *
     * <p>U6 변경 규칙:</p>
     * <p>- HsmsSettings/SocketSettings의 connectionMode는 하위 테이블 컬럼이 아니라
     *   {@code tc_eqp.comm_mode}를 기준으로 채웁니다.</p>
     * <p>- toInfo(eqp)에서 해석된 {@link ConnectionMode} 값을 재사용하여 런타임과 프로파일의 모드 기준을 일치시킵니다.</p>
     *
     * @param eqp tc_eqp 공통 설비 행
     * @return EquipmentContextRegistry에 적재할 설비 프로파일 스냅샷
     */
    private EquipmentContextProfile toProfile(final TcEqp eqp) {
        final GatewayEquipmentInfo info = toInfo(eqp);
        final long eqpKey = info.eqpKey();
        final String connectionModeTextForProfile = info.connectionMode() == null ? null : info.connectionMode().name();

        final EquipmentContextProfile.HsmsSettings hsmsSettings = hsmsStore.findByEqpKey(eqpKey)
                .map(hsms -> toHsmsSettings(hsms, connectionModeTextForProfile))
                .orElse(null);
        final EquipmentContextProfile.SocketSettings socketSettings = socketStore.findByEqpKey(eqpKey)
                .map(socket -> toSocketSettings(socket, connectionModeTextForProfile))
                .orElse(null);
        final EquipmentContextProfile.CurrentStateSnapshot currentStateSnapshot = stateStore.findByEqpKey(eqpKey)
                .map(this::toCurrentStateSnapshot)
                .orElse(null);
        final EquipmentContextProfile.LogPolicy logPolicy = logStore.findByEqpKey(eqpKey)
                .map(this::toLogPolicy)
                .orElse(null);

        final List<EquipmentContextProfile.PortStatusSnapshot> portStatuses = loadAllPortStatus(eqpKey);
        final List<EquipmentContextProfile.ParamSnapshot> params = loadAllParams(eqpKey);

        if (log.isDebugEnabled()) {
            log.debug("설비 컨텍스트 프로파일 매핑 완료. eqpId={}, commMode={}, routePartition={}, portStatusCount={}, paramCount={}",
                    info.equipmentId(),
                    info.connectionMode(),
                    info.routePartition(),
                    portStatuses.size(),
                    params.size());
        }

        return new EquipmentContextProfile(
                info,
                hsmsSettings,
                socketSettings,
                currentStateSnapshot,
                portStatuses,
                logPolicy,
                params,
                OffsetDateTime.now()
        );
    }

    /**
     * tc_eqp_port_status를 페이지 단위로 전체 조회해 스냅샷 목록으로 변환합니다.
     */
    private List<EquipmentContextProfile.PortStatusSnapshot> loadAllPortStatus(final long eqpKey) {
        final List<EquipmentContextProfile.PortStatusSnapshot> snapshots = new ArrayList<>();
        int offset = 0;
        while (true) {
            final List<TcEqpPortStatus> page = portStatusStore.findAllByEqpKey(
                    eqpKey,
                    PageRequest.of(offset, PAGE_LIMIT)
            );
            if (page.isEmpty()) {
                break;
            }
            for (TcEqpPortStatus status : page) {
                snapshots.add(new EquipmentContextProfile.PortStatusSnapshot(
                        status.portId(),
                        status.portType() == null ? null : status.portType().name(),
                        status.portState() == null ? null : status.portState().name(),
                        status.carrierId(),
                        status.carrierType() == null ? null : status.carrierType().name(),
                        status.carrierState() == null ? null : status.carrierState().name(),
                        status.updatedAt()
                ));
            }
            if (page.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }
        return snapshots;
    }

    /**
     * tc_eqp_param을 페이지 단위로 전체 조회해 스냅샷 목록으로 변환합니다.
     */
    private List<EquipmentContextProfile.ParamSnapshot> loadAllParams(final long eqpKey) {
        final List<EquipmentContextProfile.ParamSnapshot> snapshots = new ArrayList<>();
        int offset = 0;
        while (true) {
            final List<TcEqpParam> page = paramStore.findAllByEqpKey(
                    eqpKey,
                    PageRequest.of(offset, PAGE_LIMIT)
            );
            if (page.isEmpty()) {
                break;
            }
            for (TcEqpParam param : page) {
                snapshots.add(new EquipmentContextProfile.ParamSnapshot(
                        param.eqpParamKey(),
                        param.paramName(),
                        param.paramVersion(),
                        param.paramValue(),
                        param.updatedAt()
                ));
            }
            if (page.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }
        return snapshots;
    }

    /**
     * tc_eqp_hsms 행을 HSMS 설정 스냅샷으로 변환합니다.
     *
     * <p>주의:</p>
     * <p>- connectionMode는 tc_eqp_hsms 컬럼이 제거되었으므로 {@code tc_eqp.comm_mode}에서 전달받은 값을 사용합니다.</p>
     *
     * @param hsms tc_eqp_hsms 행
     * @param eqpCommMode tc_eqp.comm_mode 기준의 정규화된 연결 모드 문자열(ACTIVE/PASSIVE)
     * @return HSMS 설정 스냅샷
     */
    private EquipmentContextProfile.HsmsSettings toHsmsSettings(final TcEqpHsms hsms, final String eqpCommMode) {
        return new EquipmentContextProfile.HsmsSettings(
                hsms.deviceId(),
                eqpCommMode,
                hsms.t3Timeout(),
                hsms.t5Timeout(),
                hsms.t6Timeout(),
                hsms.t7Timeout(),
                hsms.t8Timeout(),
                hsms.linkTestEnabled(),
                hsms.linkTestInterval(),
                hsms.maxMsgBytes()
        );
    }

    /**
     * tc_eqp_socket 행을 SOCKET 설정 스냅샷으로 변환합니다.
     *
     * <p>주의:</p>
     * <p>- connectionMode는 tc_eqp_socket 컬럼이 제거되었으므로 {@code tc_eqp.comm_mode}에서 전달받은 값을 사용합니다.</p>
     *
     * @param socket tc_eqp_socket 행
     * @param eqpCommMode tc_eqp.comm_mode 기준의 정규화된 연결 모드 문자열(ACTIVE/PASSIVE)
     * @return SOCKET 설정 스냅샷
     */
    private EquipmentContextProfile.SocketSettings toSocketSettings(final TcEqpSocket socket, final String eqpCommMode) {
        return new EquipmentContextProfile.SocketSettings(
                socket.socketProtocolType(),
                eqpCommMode,
                socket.charset(),
                socket.heartbeatEnabled(),
                socket.heartbeatInterval(),
                socket.readTimeout(),
                socket.writeTimeout(),
                socket.maxFrameSizeBytes(),
                socket.keepAliveEnabled()
        );
    }

    /**
     * toCurrentStateSnapshot 기능을 수행합니다.
     *
     * @param state 입력 값
     * @return 처리 결과
     */

    private EquipmentContextProfile.CurrentStateSnapshot toCurrentStateSnapshot(final TcEqpState state) {
        return new EquipmentContextProfile.CurrentStateSnapshot(
                state.controlState() == null ? null : state.controlState().name(),
                state.eqpState() == null ? null : state.eqpState().name(),
                state.sinceAt(),
                state.reasonCode(),
                state.reasonDetail(),
                state.updatedAt()
        );
    }

    /**
     * toLogPolicy 기능을 수행합니다.
     *
     * @param logConfig 입력 값
     * @return 처리 결과
     */

    private EquipmentContextProfile.LogPolicy toLogPolicy(final TcEqpLog logConfig) {
        return new EquipmentContextProfile.LogPolicy(
                logConfig.logLevel() == null ? null : logConfig.logLevel().name(),
                logConfig.logRetentionDays(),
                logConfig.logPath(),
                logConfig.updatedAt()
        );
    }

    /**
     * tc_eqp.comm_mode 문자열을 게이트웨이 런타임 enum으로 변환합니다.
     *
     * <p>설계 배경(U6):</p>
     * <p>- connection mode의 단일 진실 소스를 하위 테이블이 아닌 {@code tc_eqp.comm_mode}로 통일합니다.</p>
     * <p>- 변환 실패 시 eqpId/원본 값을 포함한 예외를 발생시켜 데이터 오류를 빠르게 진단할 수 있도록 합니다.</p>
     *
     * @param eqp tc_eqp 공통 설비 행
     * @return 게이트웨이 기준 연결 모드 enum
     * @throws IllegalStateException comm_mode 값이 null/blank/미지원 문자열인 경우
     */
    private ConnectionMode resolveConnectionModeFromEqp(final TcEqp eqp) {
        try {
            return ConnectionMode.fromText(eqp.commMode());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid tc_eqp.comm_mode for eqpId=" + eqp.eqpId() + ", commMode=" + eqp.commMode(),
                    ex
            );
        }
    }

    /**
     * DB ProtocolType을 게이트웨이 도메인 CommInterfaceType으로 변환합니다.
     */
    private CommInterfaceType toCommInterfaceType(final ProtocolType protocolType) {
        if (protocolType == null) {
            throw new IllegalStateException("commInterface is null");
        }
        return switch (protocolType) {
            case HSMS -> CommInterfaceType.HSMS;
            case SOCKET -> CommInterfaceType.SOCKET;
        };
    }
}
