package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.core.eqp.EquipmentId;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * eqpId 바인딩/해제 서비스.
 */
@Service
public class EqpBindingService {

    private static final Logger log = LoggerFactory.getLogger(EqpBindingService.class);

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final KafkaShardOwnership shardOwnership;

    
    /**
     * 게이트웨이 Netty 어댑터 구성 요소를 초기화합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channelRegistry 통신 채널/세션 정보
     * @param processingService 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param shardOwnership 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public EqpBindingService(
            final EquipmentChannelRegistry channelRegistry,
            final GatewayProcessingService processingService,
            final KafkaShardOwnership shardOwnership
    ) {
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.shardOwnership = Objects.requireNonNull(shardOwnership, "shardOwnership is null");
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param eqpId 설비 식별 정보
     * @param interfaceType 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param channel 통신 채널/세션 정보
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public BindResult bindPassive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.PASSIVE, channel);
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param eqpId 설비 식별 정보
     * @param interfaceType 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param channel 통신 채널/세션 정보
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public BindResult bindActive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.ACTIVE, channel);
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channel 통신 채널/세션 정보
     */
    public void unbind(final Channel channel) {
        // 연결 제어 단계: 상태 전이와 예외 케이스를 함께 관리합니다.
        if (channel == null) {
            return;
        }

        final String eqpId = NettyChannelAttributes.getEqpId(channel);
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        channelRegistry.unregister(new EquipmentId(eqpId));
        processingService.removeMailbox(eqpId);
        log.info("Channel unbound. eqpId={}", eqpId);
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param eqpId 설비 식별 정보
     * @param interfaceType 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param expectedMode 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param channel 통신 채널/세션 정보
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    private BindResult bindInternal(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final ConnectionMode expectedMode,
            final Channel channel
    ) {
        // 연결 제어 단계: 상태 전이와 예외 케이스를 함께 관리합니다.
        if (eqpId == null || eqpId.isBlank()) {
            return BindResult.INVALID_EQP_ID;
        }
        if (log.isDebugEnabled()) {
            log.debug("Bind attempt. eqpId={}, interfaceType={}, mode={}", eqpId, interfaceType, expectedMode);
        }
        // shard 소유 검증: PASSIVE 등록은 반드시 ownedPartitions에 속해야 합니다.
        // shard ownership check:
        // - If the eqpId is not owned by this gateway, binding is rejected.
        // - Caller MUST close the channel immediately on NOT_OWNED.
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
        log.info("Bind success. eqpId={}, interfaceType={}, mode={}", eqpId, interfaceType, expectedMode);
        return BindResult.OK;
    }

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
