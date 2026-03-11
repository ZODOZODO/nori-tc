package com.nori.tc.db.mybatis.common.store.eqp;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocket;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpMapper;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpSocketMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TcEqpSocketMybatisStore}의 U14 PASSIVE listener-group route_partition 검증을 확인하는 단위 테스트입니다.
 *
 * <p>검증 목표:</p>
 * <p>- 동일 SOCKET listener-group(ip, port, socket_protocol_type) + PASSIVE 조건에서
 *   route_partition 불일치가 있으면 저장 전에 예외가 발생해야 합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TcEqpSocketMybatisStoreTest {

    @Mock
    private TcEqpSocketMapper socketMapper;

    @Mock
    private TcEqpMapper eqpMapper;

    /**
     * 동일 PASSIVE SOCKET listener-group인데 route_partition이 다르면 upsert를 차단해야 합니다.
     */
    @Test
    @DisplayName("U14/MyBatis SOCKET: 동일 PASSIVE listener-group route_partition 불일치 시 저장을 차단한다")
    void shouldRejectSocketUpsertWhenPassiveListenerGroupRoutePartitionMismatch() {
        final TcEqpSocketMybatisStore store = new TcEqpSocketMybatisStore(socketMapper, eqpMapper);

        final TcEqp currentParent = tcEqp(100L, "EQP-SOCKET-100", ProtocolType.SOCKET, "PASSIVE", 1, "192.168.0.13", 6000);
        final TcEqp peerParent = tcEqp(101L, "EQP-SOCKET-101", ProtocolType.SOCKET, "PASSIVE", 2, "192.168.0.13", 6000);
        final TcEqpSocket peerSocket = tcEqpSocket(101L, "LINE_DELIMITED");

        when(eqpMapper.findByEqpKey(100L)).thenReturn(Optional.of(currentParent));
        when(eqpMapper.findAll(0, 500)).thenReturn(List.of(currentParent, peerParent));
        when(socketMapper.findByEqpKey(101L)).thenReturn(Optional.of(peerSocket));

        final UpsertTcEqpSocket command = new UpsertTcEqpSocket(
                100L,
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

        assertThrows(IllegalArgumentException.class, () -> store.upsert(command));

        // 저장 로직까지 진행되면 안 되므로 update/insert는 호출되지 않아야 합니다.
        verify(socketMapper, never()).update(org.mockito.ArgumentMatchers.any());
        verify(socketMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 테스트용 tc_eqp 도메인 객체를 생성합니다.
     *
     * @param eqpKey eqp_key
     * @param eqpId eqp_id
     * @param protocolType comm_interface
     * @param commMode comm_mode
     * @param routePartition route_partition
     * @param eqpIp bind ip
     * @param eqpPort bind port
     * @return tc_eqp 도메인 객체
     */
    private TcEqp tcEqp(
            final long eqpKey,
            final String eqpId,
            final ProtocolType protocolType,
            final String commMode,
            final Integer routePartition,
            final String eqpIp,
            final int eqpPort
    ) {
        return new TcEqp(
                eqpKey,
                eqpId,
                protocolType,
                commMode,
                false,
                routePartition,
                eqpIp,
                eqpPort,
                1L,
                null,
                true,
                null,
                null,
                "SYSTEM",
                "SYSTEM"
        );
    }

    /**
     * 테스트용 tc_eqp_socket 도메인 객체를 생성합니다.
     *
     * @param eqpKey eqp_key
     * @param socketProtocolType socket_protocol_type
     * @return tc_eqp_socket 도메인 객체
     */
    private TcEqpSocket tcEqpSocket(final long eqpKey, final String socketProtocolType) {
        return new TcEqpSocket(
                eqpKey,
                socketProtocolType,
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
    }
}
