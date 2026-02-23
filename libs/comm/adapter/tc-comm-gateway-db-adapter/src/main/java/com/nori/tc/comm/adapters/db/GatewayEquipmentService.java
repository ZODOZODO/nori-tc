package com.nori.tc.comm.adapters.db;

import com.nori.tc.comm.gateway.db.ConnectionMode;
import com.nori.tc.comm.gateway.equipment.port.EquipmentInfoProvider;
import com.nori.tc.comm.gateway.context.model.EquipmentContextProfile;
import com.nori.tc.comm.gateway.context.port.EquipmentContextProfileProvider;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
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
            final TcEqpHsmsStore hsmsStore,
            final TcEqpSocketStore socketStore,
            final TcEqpStateStore stateStore,
            final TcEqpPortStatusStore portStatusStore,
            final TcEqpLogStore logStore,
            final TcEqpParamStore paramStore
    ) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        this.hsmsStore = Objects.requireNonNull(hsmsStore, "hsmsStore is null");
        this.socketStore = Objects.requireNonNull(socketStore, "socketStore is null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore is null");
        this.portStatusStore = Objects.requireNonNull(portStatusStore, "portStatusStore is null");
        this.logStore = Objects.requireNonNull(logStore, "logStore is null");
        this.paramStore = Objects.requireNonNull(paramStore, "paramStore is null");
    }

    /**
     * 전체 설비의 핵심 정보 목록을 조회합니다.
     */
    @Override
    public List<GatewayEquipmentInfo> findAll() {
        log.info("Loading equipment info list from DB.");

        final List<GatewayEquipmentInfo> results = new ArrayList<>();
        int offset = 0;
        while (true) {
            final List<TcEqp> page = eqpStore.findAll(PageRequest.of(offset, PAGE_LIMIT));
            if (page.isEmpty()) {
                break;
            }
            for (TcEqp eqp : page) {
                results.add(toInfo(eqp));
            }
            if (page.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }

        log.info("Equipment info list loaded. count={}", results.size());
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
     * 전체 설비의 컨텍스트 프로파일 목록을 조회합니다.
     */
    @Override
    public List<EquipmentContextProfile> findAllProfiles() {
        log.info("Loading equipment context profiles from DB.");

        final List<EquipmentContextProfile> results = new ArrayList<>();
        int offset = 0;
        while (true) {
            final List<TcEqp> page = eqpStore.findAll(PageRequest.of(offset, PAGE_LIMIT));
            if (page.isEmpty()) {
                break;
            }
            for (TcEqp eqp : page) {
                results.add(toProfile(eqp));
            }
            if (page.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }

        log.info("Equipment context profiles loaded. count={}", results.size());
        return results;
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

        String socketType = null;
        Integer hsmsDeviceId = null;
        ConnectionMode connectionMode = null;

        if (commInterfaceType == CommInterfaceType.SOCKET) {
            final TcEqpSocket socket = socketStore.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new IllegalStateException("Missing tc_eqp_socket for eqpId=" + eqp.eqpId()));
            socketType = socket.socketProtocolType();
            connectionMode = ConnectionMode.fromText(socket.connectionMode());
        } else if (commInterfaceType == CommInterfaceType.HSMS) {
            final TcEqpHsms hsms = hsmsStore.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new IllegalStateException("Missing tc_eqp_hsms for eqpId=" + eqp.eqpId()));
            hsmsDeviceId = hsms.deviceId();
            connectionMode = ConnectionMode.fromText(hsms.connectionMode());
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
                eqp.enabled()
        );
    }

    /**
     * tc_eqp 계열 테이블을 조합해서 EquipmentContextProfile을 구성합니다.
     */
    private EquipmentContextProfile toProfile(final TcEqp eqp) {
        final GatewayEquipmentInfo info = toInfo(eqp);
        final long eqpKey = info.eqpKey();

        final EquipmentContextProfile.HsmsSettings hsmsSettings = hsmsStore.findByEqpKey(eqpKey)
                .map(this::toHsmsSettings)
                .orElse(null);
        final EquipmentContextProfile.SocketSettings socketSettings = socketStore.findByEqpKey(eqpKey)
                .map(this::toSocketSettings)
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
            log.debug("Equipment context profile mapped. eqpId={}, portStatusCount={}, paramCount={}",
                    info.equipmentId(),
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
     * toHsmsSettings 기능을 수행합니다.
     *
     * @param hsms 입력 값
     * @return 처리 결과
     */

    private EquipmentContextProfile.HsmsSettings toHsmsSettings(final TcEqpHsms hsms) {
        return new EquipmentContextProfile.HsmsSettings(
                hsms.deviceId(),
                hsms.connectionMode(),
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
     * toSocketSettings 기능을 수행합니다.
     *
     * @param socket 입력 값
     * @return 처리 결과
     */

    private EquipmentContextProfile.SocketSettings toSocketSettings(final TcEqpSocket socket) {
        return new EquipmentContextProfile.SocketSettings(
                socket.socketProtocolType(),
                socket.connectionMode(),
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
