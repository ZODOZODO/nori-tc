package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.gateway.config.props.GatewayHsmsProperties;
import com.nori.tc.comm.gateway.config.props.GatewayPublishPolicyProperties;
import com.nori.tc.comm.gateway.config.props.GatewayRuntimeProperties;
import com.nori.tc.comm.gateway.config.props.GatewaySocketProperties;
import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.hsms.config.HsmsSessionConfig;
import com.nori.tc.comm.gateway.hsms.session.HsmsSessionStateMachine;
import com.nori.tc.comm.gateway.socket.config.SocketTypeConfig;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * eqp 프로파일(DB) + 설정(properties) -> 런타임 컨텍스트 생성
 */
@Component
public class EquipmentContextFactory {

    private static final Logger log = LoggerFactory.getLogger(EquipmentContextFactory.class);
    private final GatewayRuntimeProperties runtimeProperties;
    private final GatewayHsmsProperties hsmsProperties;
    private final GatewaySocketProperties socketProperties;
    private final GatewayPublishPolicyProperties publishPolicyProperties;
    private final SocketTypeRegistry socketTypeRegistry;

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param runtimeProperties 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param hsmsProperties 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param socketProperties 통신 채널/세션 정보
     * @param publishPolicyProperties 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param socketTypeRegistry 통신 채널/세션 정보
     */
    public EquipmentContextFactory(
            final GatewayRuntimeProperties runtimeProperties,
            final GatewayHsmsProperties hsmsProperties,
            final GatewaySocketProperties socketProperties,
            final GatewayPublishPolicyProperties publishPolicyProperties,
            final SocketTypeRegistry socketTypeRegistry
    ) {
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");
        this.hsmsProperties = Objects.requireNonNull(hsmsProperties, "hsmsProperties is null");
        this.socketProperties = Objects.requireNonNull(socketProperties, "socketProperties is null");
        this.publishPolicyProperties = Objects.requireNonNull(publishPolicyProperties, "publishPolicyProperties is null");
        this.socketTypeRegistry = Objects.requireNonNull(socketTypeRegistry, "socketTypeRegistry is null");
    }

    
    /**
     * 장비 정보와 공유 inbound 큐를 사용하여 런타임 컨텍스트를 생성합니다.
     *
     * <p>이 메서드는 {@link EqpMailboxRegistry}가 생성한 bounded inbound 큐를 그대로 받아
     * mailbox와 runtime context가 동일한 큐 인스턴스를 공유하도록 보장합니다.</p>
     *
     * <p>생성 규칙 요약:</p>
     * <p>1) DB 장비 정보와 전역 설정(properties)을 조합하여 {@link EquipmentProfile}을 구성합니다.</p>
     * <p>2) 공통 재조립 버퍼와 태그를 생성합니다.</p>
     * <p>3) 인터페이스 타입(HSMS/SOCKET)에 따라 적절한 런타임 컨텍스트 구현체를 생성합니다.</p>
     *
     * @param info 장비 연결/프로토콜 정보를 포함한 장비 메타 정보
     * @param inboundQueue mailbox와 공유할 bounded inbound 큐
     * @return 장비 인터페이스 타입에 맞는 런타임 컨텍스트 구현체
     */
    public EquipmentRuntimeContext create(
            final GatewayEquipmentInfo info,
            final BoundedInboundQueue inboundQueue
    ) {
        Objects.requireNonNull(info, "info is null");
        Objects.requireNonNull(inboundQueue, "inboundQueue is null");

        final EquipmentId equipmentId = new EquipmentId(info.equipmentId());
        final CommInterfaceType interfaceType = info.commInterfaceType();

        final String socketType = (info.socketType() == null || info.socketType().isBlank())
                ? socketProperties.getDefaultSocketType()
                : info.socketType();

        if (log.isDebugEnabled()) {
            log.debug("Creating runtime context. eqpId={}, interfaceType={}, socketType={}",
                    info.equipmentId(), interfaceType, socketType);
        }

        final EquipmentProfile profile = new EquipmentProfile(
                equipmentId,
                interfaceType,
                interfaceType == CommInterfaceType.SOCKET ? socketType : null
        );

        final ReassemblyBuffer reassemblyBuffer = new ReassemblyBuffer(
                runtimeProperties.getReassemblyInitialBytes(),
                runtimeProperties.getReassemblyMaxBytes()
        );

        final Map<String, String> tags = Map.of(
                "policyVersion", publishPolicyProperties.getVersion(),
                "socketType", socketType
        );

        if (interfaceType == CommInterfaceType.HSMS) {
            final int deviceId = (info.hsmsDeviceId() == null)
                    ? hsmsProperties.getDeviceId()
                    : info.hsmsDeviceId();

            final HsmsSessionConfig sessionConfig = hsmsProperties.toSessionConfig(deviceId);
            final HsmsSessionStateMachine sessionStateMachine = new HsmsSessionStateMachine(sessionConfig);

            if (log.isDebugEnabled()) {
                log.debug("HSMS 런타임 컨텍스트 생성 분기 선택. eqpId={}, deviceId={}, inboundQueueCapacityShared=true",
                        info.equipmentId(),
                        deviceId);
            }

            return new GatewayHsmsRuntimeContext(
                    profile,
                    inboundQueue,
                    reassemblyBuffer,
                    tags,
                    sessionStateMachine
            );
        }

        final SocketTypeConfig socketTypeConfig = new SocketTypeConfig(
                socketType,
                socketProperties.getMaxFrameBytes(),
                socketProperties.isAllowEmptyFrame()
        );

        if (log.isDebugEnabled()) {
            log.debug("SOCKET 런타임 컨텍스트 생성 분기 선택. eqpId={}, socketType={}, maxFrameBytes={}, allowEmptyFrame={}",
                    info.equipmentId(),
                    socketType,
                    socketProperties.getMaxFrameBytes(),
                    socketProperties.isAllowEmptyFrame());
        }

        return new GatewaySocketRuntimeContext(
                profile,
                inboundQueue,
                reassemblyBuffer,
                tags,
                socketTypeConfig,
                socketTypeRegistry
        );
    }
}
