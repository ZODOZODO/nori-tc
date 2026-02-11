package com.nori.tc.apps.commgateway.db;

import com.nori.tc.apps.commgateway.comm.ConnectionMode;
import com.nori.tc.apps.commgateway.comm.EquipmentInfoProvider;
import com.nori.tc.apps.commgateway.metrics.GatewayLogContext;
import com.nori.tc.comm.domain.type.CommInterfaceType;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.store.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 게이트웨이 설비 조회 서비스(DB 어댑터).
 *
 * - 코어의 EquipmentInfoProvider 포트를 구현
 * - tc_eqp / tc_eqp_hsms / tc_eqp_socket을 조합해 런타임용 정보를 만든다
 * - 앱별로 조회/조합 규칙이 달라질 수 있어 어댑터로 분리한다
 */
@Service
public class GatewayEquipmentService implements EquipmentInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(GatewayEquipmentService.class);

    private static final int PAGE_LIMIT = PageRequest.defaultPage().limit();

    private final TcEqpStore eqpStore;
    private final TcEqpHsmsStore hsmsStore;
    private final TcEqpSocketStore socketStore;

    public GatewayEquipmentService(
            final TcEqpStore eqpStore,
            final TcEqpHsmsStore hsmsStore,
            final TcEqpSocketStore socketStore
    ) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        this.hsmsStore = Objects.requireNonNull(hsmsStore, "hsmsStore is null");
        this.socketStore = Objects.requireNonNull(socketStore, "socketStore is null");
    }

    @Override
    public List<GatewayEquipmentInfo> findAll() {
        log.info("Loading equipment list from DB.");
        final List<GatewayEquipmentInfo> results = new ArrayList<>();
        int offset = 0;

        while (true) {
            // Offset/limit works for both JPA and MyBatis store implementations.
            final List<TcEqp> page = eqpStore.findAll(PageRequest.of(offset, PAGE_LIMIT));
            if (page.isEmpty()) {
                break;
            }
            if (log.isDebugEnabled()) {
                log.debug("Loaded equipment page. offset={}, size={}", offset, page.size());
            }
            for (TcEqp eqp : page) {
                results.add(toInfo(eqp));
            }
            if (page.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }

        log.info("Equipment list loaded. count={}", results.size());
        return results;
    }

    @Override
    public Optional<GatewayEquipmentInfo> findById(final String equipmentId) {
        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(equipmentId)) {
            if (log.isDebugEnabled()) {
                log.debug("Loading equipment by eqpId={}", equipmentId);
            }
            return eqpStore.findByEqpId(equipmentId).map(this::toInfo);
        }
    }

    private GatewayEquipmentInfo toInfo(final TcEqp eqp) {
        if (eqp == null) {
            throw new IllegalArgumentException("eqp is null");
        }

        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqp.eqpId())) {

            // eqpKey is the PK used by tc_eqp_hsms and tc_eqp_socket.
            final Long eqpKey = eqp.eqpKey();
            if (eqpKey == null || eqpKey <= 0) {
                throw new IllegalStateException("Invalid eqpKey for eqpId=" + eqp.eqpId());
            }
            if (eqp.eqpId() == null || eqp.eqpId().isBlank()) {
                throw new IllegalStateException("Invalid eqpId for eqpKey=" + eqpKey);
            }

            // ProtocolType (db-domain) -> CommInterfaceType (comm-domain).
            final CommInterfaceType commInterfaceType = toCommInterfaceType(eqp.commInterface());

            String socketType = null;
            Integer hsmsDeviceId = null;
            ConnectionMode connectionMode = null;

            if (commInterfaceType == CommInterfaceType.SOCKET) {
                // SOCKET uses tc_eqp_socket.socket_protocol_type as socketType.
                final TcEqpSocket socket = socketStore.findByEqpKey(eqpKey)
                        .orElseThrow(() -> new IllegalStateException("Missing tc_eqp_socket for eqpId=" + eqp.eqpId()));
                socketType = socket.socketProtocolType();
                connectionMode = ConnectionMode.fromText(socket.connectionMode());
            } else if (commInterfaceType == CommInterfaceType.HSMS) {
                // HSMS uses tc_eqp_hsms.device_id as deviceId.
                final TcEqpHsms hsms = hsmsStore.findByEqpKey(eqpKey)
                        .orElseThrow(() -> new IllegalStateException("Missing tc_eqp_hsms for eqpId=" + eqp.eqpId()));
                hsmsDeviceId = hsms.deviceId();
                connectionMode = ConnectionMode.fromText(hsms.connectionMode());
            }

            return new GatewayEquipmentInfo(
                    eqp.eqpId(),
                    commInterfaceType,
                    socketType,
                    hsmsDeviceId,
                    eqp.eqpIp(),
                    eqp.eqpPort(),
                    connectionMode,
                    eqp.enabled()
            );
        }
    }

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
