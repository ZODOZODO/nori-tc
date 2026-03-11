package com.nori.tc.db.mybatis.common.store.eqp;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpHsms;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpHsmsMapper;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpMapper;
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
 * {@link TcEqpHsmsMybatisStore}의 U14 PASSIVE listener-group route_partition 검증을 확인하는 단위 테스트입니다.
 *
 * <p>검증 목적:</p>
 * <p>- 동일 HSMS listener-group(ip, port) + PASSIVE 조건에서 route_partition이 다르면
 * 저장 전에 즉시 차단되는지 확인합니다.</p>
 *
 * <p>주의:</p>
 * <p>- 본 테스트는 MyBatis/DB를 직접 호출하지 않고 Mapper를 Mockito mock으로 대체합니다.</p>
 * <p>- 핵심은 "검증 실패 시 update/insert가 호출되지 않는다"는 점입니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TcEqpHsmsMybatisStoreTest {

    @Mock
    private TcEqpHsmsMapper hsmsMapper;

    @Mock
    private TcEqpMapper eqpMapper;

    /**
     * 동일 PASSIVE HSMS listener-group인데 route_partition이 다르면 upsert를 차단해야 합니다.
     *
     * <p>listener-group 기준(HSMS):</p>
     * <p>- comm_interface=HSMS</p>
     * <p>- comm_mode=PASSIVE</p>
     * <p>- 동일 eqp_ip + eqp_port</p>
     */
    @Test
    @DisplayName("U14/MyBatis HSMS: 동일 PASSIVE listener-group route_partition 불일치 시 저장을 차단한다")
    void shouldRejectHsmsUpsertWhenPassiveListenerGroupRoutePartitionMismatch() {
        // 저장소 생성: U14 검증 로직이 포함된 MyBatis Store 구현체를 생성합니다.
        final TcEqpHsmsMybatisStore store = new TcEqpHsmsMybatisStore(hsmsMapper, eqpMapper);

        // 현재 저장 대상(parent tc_eqp): HSMS + PASSIVE + route_partition=1
        final TcEqp currentParent = tcEqp(
                200L,
                "EQP-HSMS-200",
                ProtocolType.SECS,
                "PASSIVE",
                1,
                "192.168.0.30",
                5000
        );

        // 동일 listener-group 후보(peer tc_eqp): HSMS + PASSIVE + 동일 bind(ip/port) + route_partition=2 (불일치)
        final TcEqp peerParent = tcEqp(
                201L,
                "EQP-HSMS-201",
                ProtocolType.SECS,
                "PASSIVE",
                2,
                "192.168.0.30",
                5000
        );

        // 검증 로직이 참조하는 parent/전체 목록 조회 결과를 stub 처리합니다.
        when(eqpMapper.findByEqpKey(200L)).thenReturn(Optional.of(currentParent));
        when(eqpMapper.findAll(0, 500)).thenReturn(List.of(currentParent, peerParent));

        // HSMS 상세 upsert 입력(유효한 최소값 + 검증에 필요한 deviceId 포함)
        final UpsertTcEqpHsms command = new UpsertTcEqpHsms(
                200L,
                1001,
                45,
                10,
                10,
                10,
                10,
                true,
                30,
                1024L,
                null,
                null
        );

        // 실행/검증: route_partition 불일치가 감지되면 저장 전에 예외가 발생해야 합니다.
        assertThrows(IllegalArgumentException.class, () -> store.upsert(command));

        // 중요 검증: 저장 로직(update/insert)까지 내려가면 안 됩니다.
        verify(hsmsMapper, never()).update(any());
        verify(hsmsMapper, never()).insert(any());
    }

    /**
     * 테스트용 tc_eqp 도메인 객체를 생성합니다.
     *
     * <p>U14 검증은 parent tc_eqp의 comm_mode/route_partition/bind 정보만 사용하므로,
     * 나머지 필드는 테스트 목적에 맞는 최소값으로 채웁니다.</p>
     *
     * @param eqpKey eqp_key
     * @param eqpId eqp_id
     * @param protocolType comm_interface
     * @param commMode tc_eqp.comm_mode
     * @param routePartition tc_eqp.route_partition
     * @param eqpIp bind ip
     * @param eqpPort bind port
     * @return 테스트용 tc_eqp 도메인 객체
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
}
