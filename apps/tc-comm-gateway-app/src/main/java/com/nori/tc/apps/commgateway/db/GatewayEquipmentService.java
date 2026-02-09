package com.nori.tc.apps.commgateway.db;

import com.nori.tc.comm.domain.type.CommInterfaceType;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.store.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Gateway equipment lookup service.
 *
 * - Aggregates tc_eqp + tc_eqp_hsms/tc_eqp_socket for runtime use.
 * - Uses DB starter implementations via core store interfaces.
 */
@Service
public class GatewayEquipmentService {

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

    public List<GatewayEquipmentInfo> findAll() {
        final List<GatewayEquipmentInfo> results = new ArrayList<>();
        int offset = 0;

        while (true) {
            // Store는 공통 PageRequest를 받는다.
            // - JPA/MyBatis 구현 차이를 감추기 위해 offset/limit 방식으로 통일
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

        return results;
    }

    public Optional<GatewayEquipmentInfo> findById(final String equipmentId) {
        return eqpStore.findByEqpId(equipmentId).map(this::toInfo);
    }

    private GatewayEquipmentInfo toInfo(final TcEqp eqp) {
        if (eqp == null) {
            throw new IllegalArgumentException("eqp is null");
        }

        // eqpKey는 하위 테이블(tc_eqp_hsms / tc_eqp_socket)의 PK/FK이므로 필수
        final Long eqpKey = eqp.eqpKey();
        if (eqpKey == null || eqpKey <= 0) {
            throw new IllegalStateException("Invalid eqpKey for eqpId=" + eqp.eqpId());
        }
        if (eqp.eqpId() == null || eqp.eqpId().isBlank()) {
            throw new IllegalStateException("Invalid eqpId for eqpKey=" + eqpKey);
        }

        // ProtocolType(db-domain) -> CommInterfaceType(comm-domain) 변환
        final CommInterfaceType commInterfaceType = toCommInterfaceType(eqp.commInterface());

        String socketType = null;
        Integer hsmsDeviceId = null;

        if (commInterfaceType == CommInterfaceType.SOCKET) {
            // SOCKET은 tc_eqp_socket.socket_protocol_type을 socketType으로 사용
            socketType = socketStore.findByEqpKey(eqpKey)
                    .map(TcEqpSocket::socketProtocolType)
                    .orElse(null);
        } else if (commInterfaceType == CommInterfaceType.HSMS) {
            // HSMS는 tc_eqp_hsms.device_id를 deviceId로 사용
            hsmsDeviceId = hsmsStore.findByEqpKey(eqpKey)
                    .map(TcEqpHsms::deviceId)
                    .orElse(null);
        }

        return new GatewayEquipmentInfo(
                eqp.eqpId(),
                commInterfaceType,
                socketType,
                hsmsDeviceId,
                eqp.enabled()
        );
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
