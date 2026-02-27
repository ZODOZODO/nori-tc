package com.nori.tc.db.jpa.common.store.eqp;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocket;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpEntity;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpSocketEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpSocketEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpJpaRepository;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpSocketJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TcEqpSocketJpaStore}의 U14 PASSIVE listener-group route_partition 검증 회귀를 확인하는 단위 테스트입니다.
 *
 * <p>검증 목적:</p>
 * <p>- 동일 PASSIVE SOCKET listener-group(ip, port, socket_protocol_type) 조건에서
 * route_partition이 다르면 저장 전에 차단되는지 확인합니다.</p>
 *
 * <p>테스트 방식:</p>
 * <p>- JPA Repository/Mapper는 Mockito mock을 사용합니다.</p>
 * <p>- 핵심은 예외 발생 및 {@code repository.save(...)} 미호출 여부입니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TcEqpSocketJpaStoreTest {

    @Mock
    private TcEqpSocketJpaRepository socketRepository;

    @Mock
    private TcEqpJpaRepository eqpRepository;

    @Mock
    private TcEqpSocketEntityMapper mapper;

    /**
     * 동일 PASSIVE SOCKET listener-group에서 route_partition 불일치가 발생하면 저장을 차단해야 합니다.
     */
    @Test
    @DisplayName("U14/JPA SOCKET: 동일 PASSIVE listener-group route_partition 불일치 시 저장을 차단한다")
    void shouldRejectSocketUpsertWhenPassiveListenerGroupRoutePartitionMismatch() {
        // 저장소 생성: U14 검증 로직이 포함된 JPA Store 구현체를 생성합니다.
        final TcEqpSocketJpaStore store = new TcEqpSocketJpaStore(socketRepository, eqpRepository, mapper);

        // 현재 저장 대상 parent(tc_eqp)
        final TcEqpEntity currentParent = passiveEqp(
                300L,
                "EQP-SOCKET-300",
                ProtocolType.SOCKET,
                1,
                "192.168.0.40",
                7000
        );

        // 동일 listener-group peer(tc_eqp) - route_partition만 다름
        final TcEqpEntity peerParent = passiveEqp(
                301L,
                "EQP-SOCKET-301",
                ProtocolType.SOCKET,
                2,
                "192.168.0.40",
                7000
        );

        // peer 상세(socket_protocol_type)도 동일하게 맞춰서 최종 listener-group 후보가 되도록 구성합니다.
        final TcEqpSocketEntity peerSocket = new TcEqpSocketEntity();
        peerSocket.setEqpKey(301L);
        peerSocket.setSocketProtocolType("LINE_DELIMITED");

        when(eqpRepository.findById(300L)).thenReturn(Optional.of(currentParent));
        when(eqpRepository.findAll()).thenReturn(List.of(currentParent, peerParent));
        when(socketRepository.findById(301L)).thenReturn(Optional.of(peerSocket));

        final UpsertTcEqpSocket command = new UpsertTcEqpSocket(
                300L,
                "line_delimited",
                "UTF-8",
                true,
                30,
                1000,
                1000,
                8192,
                true,
                null,
                null
        );

        // 실행/검증: listener-group route_partition 불일치 시 저장 전에 예외가 발생해야 합니다.
        assertThrows(IllegalArgumentException.class, () -> store.upsert(command));

        // 중요 검증: 검증 실패 시 실제 저장 로직으로 내려가면 안 됩니다.
        verify(socketRepository, never()).save(any(TcEqpSocketEntity.class));
        verify(mapper, never()).updateEntity(any(UpsertTcEqpSocket.class), any(TcEqpSocketEntity.class));
    }

    /**
     * PASSIVE 설비용 parent tc_eqp 엔티티를 생성합니다.
     *
     * <p>U14 검증은 parent 엔티티의 공통 통신 정보(comm_interface, comm_mode, route_partition, bind ip/port)를 사용하므로
     * 테스트에 필요한 필드만 명시적으로 채웁니다.</p>
     *
     * @param eqpKey eqp_key
     * @param eqpId eqp_id
     * @param commInterface comm_interface
     * @param routePartition route_partition
     * @param eqpIp bind ip
     * @param eqpPort bind port
     * @return 테스트용 parent tc_eqp 엔티티
     */
    private TcEqpEntity passiveEqp(
            final long eqpKey,
            final String eqpId,
            final ProtocolType commInterface,
            final Integer routePartition,
            final String eqpIp,
            final int eqpPort
    ) {
        final TcEqpEntity entity = new TcEqpEntity();
        entity.setEqpKey(eqpKey);
        entity.setEqpId(eqpId);
        entity.setCommInterface(commInterface);
        entity.setCommMode("PASSIVE");
        entity.setRoutePartition(routePartition);
        entity.setEqpIp(eqpIp);
        entity.setEqpPort(eqpPort);
        entity.setModelVersionKey(1L);
        entity.setEnabled(true);
        entity.setCreatedBy("SYSTEM");
        entity.setUpdatedBy("SYSTEM");
        return entity;
    }
}
