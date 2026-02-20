package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.gateway.lifecycle.EqpLifecycleStateMachine;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Netty 채널을 설비(eqpId)에 바인딩/해제하는 서비스입니다.
 *
 * <p>주요 책임은 다음과 같습니다.</p>
 * <p>1) 바인딩 전 공통 검증(shard ownership, DB 등록, enabled, interfaceType, connectionMode)</p>
 * <p>2) channelRegistry 등록 및 mailbox 생성</p>
 * <p>3) 연결/해제 라이프사이클 이벤트 발행</p>
 *
 * <p>중요: connectionMode는 설비 관점으로 해석합니다.</p>
 * <p>- 설비 ACTIVE  : 설비가 접속 주체(게이트웨이는 수신 서버)</p>
 * <p>- 설비 PASSIVE : 게이트웨이가 접속 주체(아웃바운드 클라이언트)</p>
 */
@Service
public class EqpBindingService {

    private static final Logger log = LoggerFactory.getLogger(EqpBindingService.class);

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final KafkaShardOwnership shardOwnership;
    private final EqpLifecycleStateMachine lifecycleStateMachine;

    /**
     * 바인딩 서비스 의존성을 주입받아 초기화합니다.
     *
     * @param channelRegistry 설비 채널 레지스트리
     * @param processingService mailbox/설비 조회 처리 서비스
     * @param shardOwnership 현재 인스턴스 shard 소유 판단기
     * @param lifecycleStateMachine 라이프사이클 상태머신
     */
    public EqpBindingService(
            final EquipmentChannelRegistry channelRegistry,
            final GatewayProcessingService processingService,
            final KafkaShardOwnership shardOwnership,
            final EqpLifecycleStateMachine lifecycleStateMachine
    ) {
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.shardOwnership = Objects.requireNonNull(shardOwnership, "shardOwnership is null");
        this.lifecycleStateMachine = Objects.requireNonNull(lifecycleStateMachine, "lifecycleStateMachine is null");
    }

    /**
     * 서버 수신(PASSIVE handler) 경로 채널을 바인딩합니다.
     *
     * <p>수신 경로는 "설비가 먼저 접속"한 경우이므로, 설비 connectionMode는 ACTIVE여야 합니다.</p>
     *
     * @param eqpId 파싱된 설비 ID
     * @param interfaceType 통신 인터페이스(HSMS/SOCKET)
     * @param channel Netty 채널
     * @return 바인딩 결과 코드
     */
    public BindResult bindPassive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.ACTIVE, channel);
    }

    /**
     * 아웃바운드 클라이언트(ACTIVE handler) 경로 채널을 바인딩합니다.
     *
     * <p>발신 경로는 "게이트웨이가 먼저 접속"한 경우이므로, 설비 connectionMode는 PASSIVE여야 합니다.</p>
     *
     * @param eqpId 검증된 설비 ID
     * @param interfaceType 통신 인터페이스(HSMS/SOCKET)
     * @param channel Netty 채널
     * @return 바인딩 결과 코드
     */
    public BindResult bindActive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.PASSIVE, channel);
    }

    /**
     * 채널 해제를 수행합니다.
     *
     * <p>채널에 저장된 eqpId를 기준으로 channelRegistry/mailbox를 정리하고,
     * 상태머신에 disconnected 이벤트를 전달합니다.</p>
     *
     * @param channel 해제할 Netty 채널
     */
    public void unbind(final Channel channel) {
        if (channel == null) {
            return;
        }

        final String eqpId = NettyChannelAttributes.getEqpId(channel);
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        channelRegistry.unregister(new EquipmentId(eqpId));
        processingService.removeMailbox(eqpId);
        lifecycleStateMachine.onChannelDisconnected(eqpId, "SYSTEM", "NETTY_UNBIND");

        log.info("Channel unbound. eqpId={}", eqpId);
    }

    /**
     * 공통 바인딩 검증과 실제 레지스트리/메일박스 등록을 수행합니다.
     *
     * <p>검증 순서:</p>
     * <p>1) eqpId 유효성</p>
     * <p>2) shard ownership</p>
     * <p>3) DB 등록 설비 조회</p>
     * <p>4) enabled, interfaceType, connectionMode 일치</p>
     * <p>5) 중복 연결 여부</p>
     *
     * @param eqpId 대상 설비 ID
     * @param interfaceType 채널 인터페이스 타입
     * @param expectedMode 현재 바인딩 경로가 요구하는 설비 connectionMode
     * @param channel Netty 채널
     * @return 바인딩 결과 코드
     */
    private BindResult bindInternal(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final ConnectionMode expectedMode,
            final Channel channel
    ) {
        if (eqpId == null || eqpId.isBlank()) {
            return BindResult.INVALID_EQP_ID;
        }
        if (log.isDebugEnabled()) {
            log.debug("Bind attempt. eqpId={}, interfaceType={}, expectedEquipmentMode={}",
                    eqpId, interfaceType, expectedMode);
        }

        if (!shardOwnership.isOwned(eqpId)) {
            return BindResult.NOT_OWNED;
        }

        final GatewayEquipmentInfo info;
        try {
            info = processingService.resolveEquipment(eqpId);
        } catch (Exception ex) {
            return BindResult.UNKNOWN_EQUIPMENT;
        }

        if (!info.enabled()) {
            return BindResult.DISABLED;
        }
        if (info.commInterfaceType() != interfaceType) {
            return BindResult.COMM_INTERFACE_MISMATCH;
        }
        if (info.connectionMode() != expectedMode) {
            return BindResult.CONNECTION_MODE_MISMATCH;
        }

        final NettyEquipmentChannel equipmentChannel = new NettyEquipmentChannel(channel);
        final boolean bound = channelRegistry.tryBind(new EquipmentId(eqpId), equipmentChannel);
        if (!bound) {
            return BindResult.DUPLICATE_CONNECTION;
        }

        processingService.bindMailbox(info, equipmentChannel);
        lifecycleStateMachine.onChannelConnected(eqpId, expectedMode.name(), "NETTY_BIND");

        log.info("Bind success. eqpId={}, interfaceType={}, equipmentMode={}", eqpId, interfaceType, expectedMode);
        return BindResult.OK;
    }

    /**
     * 채널 바인딩 결과 코드입니다.
     */
    public enum BindResult {
        OK,
        NOT_OWNED,
        DUPLICATE_CONNECTION,
        DISABLED,
        UNKNOWN_EQUIPMENT,
        INVALID_EQP_ID,
        COMM_INTERFACE_MISMATCH,
        CONNECTION_MODE_MISMATCH
    }
}
