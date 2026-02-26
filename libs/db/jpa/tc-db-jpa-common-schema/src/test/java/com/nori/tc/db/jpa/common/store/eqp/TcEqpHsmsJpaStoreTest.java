package com.nori.tc.db.jpa.common.store.eqp;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpHsms;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpEntity;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpHsmsEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpHsmsEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpHsmsJpaRepository;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpJpaRepository;
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
 * {@link TcEqpHsmsJpaStore}의 U14 PASSIVE listener-group route_partition 검증 회귀를 확인하는 단위 테스트입니다.
 *
 * <p>검증 포인트:</p>
 * <p>- 동일 HSMS PASSIVE listener-group(ip, port)에서 route_partition이 다르면
 * 저장 전에 예외가 발생해야 합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TcEqpHsmsJpaStoreTest {

    @Mock
    private TcEqpHsmsJpaRepository hsmsRepository;

    @Mock
    private TcEqpJpaRepository eqpRepository;

    @Mock
    private TcEqpHsmsEntityMapper mapper;

    /**
     * 동일 PASSIVE HSMS listener-group에서 route_partition이 다르면 upsert를 차단해야 합니다.
     */
    @Test
    @DisplayName("U14/JPA HSMS: 동일 PASSIVE listener-group route_partition 불일치 시 저장을 차단한다")
    void shouldRejectHsmsUpsertWhenPassiveListenerGroupRoutePartitionMismatch() {
        // 저장소 생성: U14 listener-group 검증 로직 포함 구현체
        final TcEqpHsmsJpaStore store = new TcEqpHsmsJpaStore(hsmsRepository, eqpRepository, mapper);

        final TcEqpEntity currentParent = passiveEqp(
                400L,
                "EQP-HSMS-400",
                ProtocolType.HSMS,
                1,
                "192.168.0.50",
                5000
        );
        final TcEqpEntity peerParent = passiveEqp(
                401L,
                "EQP-HSMS-401",
                ProtocolType.HSMS,
                3,
                "192.168.0.50",
                5000
        );

        when(eqpRepository.findById(400L)).thenReturn(Optional.of(currentParent));
        when(eqpRepository.findAll()).thenReturn(List.of(currentParent, peerParent));

        final UpsertTcEqpHsms command = new UpsertTcEqpHsms(
                400L,
                2001,
                45,
                10,
                10,
                10,
                10,
                true,
                30,
                4096L,
                null,
                null
        );

        // 실행/검증: route_partition 불일치가 감지되면 저장 전에 예외가 발생해야 합니다.
        assertThrows(IllegalArgumentException.class, () -> store.upsert(command));

        // 저장 단계로 내려가면 안 되므로 repository.save / mapper.updateEntity 모두 미호출이어야 합니다.
        verify(hsmsRepository, never()).save(any(TcEqpHsmsEntity.class));
        verify(mapper, never()).updateEntity(any(UpsertTcEqpHsms.class), any(TcEqpHsmsEntity.class));
    }

    /**
     * PASSIVE HSMS 설비용 parent tc_eqp 엔티티를 생성합니다.
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
        entity.setModelKey(1L);
        entity.setEnabled(true);
        entity.setCreatedBy("SYSTEM");
        entity.setUpdatedBy("SYSTEM");
        return entity;
    }
}
