package com.nori.tc.comm.gateway.runtime.context;

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
import com.nori.tc.comm.gateway.runtime.mailbox.BoundedInboundQueue;
import com.nori.tc.comm.gateway.runtime.mailbox.EquipmentMailboxRegistry;
import com.nori.tc.comm.gateway.socket.config.SocketTypeConfig;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 설비 메타 정보(DB 조회 결과)와 게이트웨이 설정을 조합하여 프로토콜별 런타임 컨텍스트를 생성하는 팩토리입니다.
 *
 * <p>이 팩토리는 설비별 mailbox 생성 시점에 호출되며, 다음 항목을 한 번에 구성합니다.</p>
 * <p>1) {@link EquipmentProfile} (공통 프로파일)</p>
 * <p>2) {@link ReassemblyBuffer} (프레임 재조립 버퍼)</p>
 * <p>3) 공통 태그(tags)</p>
 * <p>4) 프로토콜별 런타임 컨텍스트 구현체(HSMS/SOCKET)</p>
 */
@Component
public class EquipmentRuntimeContextFactory {

    private static final Logger log = LoggerFactory.getLogger(EquipmentRuntimeContextFactory.class);
    private final GatewayRuntimeProperties runtimeProperties;
    private final GatewayHsmsProperties hsmsProperties;
    private final GatewaySocketProperties socketProperties;
    private final GatewayPublishPolicyProperties publishPolicyProperties;
    private final SocketTypeRegistry socketTypeRegistry;

    /**
     * 런타임 컨텍스트 팩토리 의존성을 초기화합니다.
     *
     * @param runtimeProperties 공통 런타임 설정(버퍼 크기 등)
     * @param hsmsProperties HSMS 기본 설정(디바이스 ID/타이머 등)
     * @param socketProperties SOCKET 기본 설정(socketType/frame 정책 등)
     * @param publishPolicyProperties 발행 정책 버전 등 공통 태그 생성용 설정
     * @param socketTypeRegistry SOCKET 타입 핸들러 레지스트리
     */
    public EquipmentRuntimeContextFactory(
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

        log.info(
                "EquipmentRuntimeContextFactory initialized. reassemblyInitialBytes={}, reassemblyMaxBytes={}, defaultSocketType={}",
                runtimeProperties.getReassemblyInitialBytes(),
                runtimeProperties.getReassemblyMaxBytes(),
                socketProperties.getDefaultSocketType()
        );
    }

    /**
     * 장비 정보와 공유 inbound 큐를 사용하여 런타임 컨텍스트를 생성합니다.
     *
     * <p>이 메서드는 {@link EquipmentMailboxRegistry}가 생성한 bounded inbound 큐를 그대로 받아
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
        final CommInterfaceType interfaceType = Objects.requireNonNull(
                info.commInterfaceType(),
                "info.commInterfaceType is null"
        );

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

        // 재조립 버퍼는 설비별로 독립 생성하여 파서 상태가 섞이지 않도록 합니다.
        final ReassemblyBuffer reassemblyBuffer = new ReassemblyBuffer(
                runtimeProperties.getReassemblyInitialBytes(),
                runtimeProperties.getReassemblyMaxBytes()
        );

        // 공통 운영 태그는 DLQ/메트릭/로그 컨텍스트에서 재사용됩니다.
        final Map<String, String> tags = Map.of(
                "policyVersion", publishPolicyProperties.getVersion(),
                "socketType", socketType
        );

        if (interfaceType == CommInterfaceType.SECS) {
            final int deviceId = (info.hsmsDeviceId() == null)
                    ? hsmsProperties.getDeviceId()
                    : info.hsmsDeviceId();

            // HSMS는 설비별 세션 상태 머신을 별도로 가져야 하므로 생성 시점에 함께 준비합니다.
            final HsmsSessionConfig sessionConfig = hsmsProperties.toSessionConfig(deviceId);
            final HsmsSessionStateMachine sessionStateMachine = new HsmsSessionStateMachine(sessionConfig);

            if (log.isDebugEnabled()) {
                log.debug("HSMS 런타임 컨텍스트 생성 분기 선택. eqpId={}, deviceId={}, inboundQueueCapacityShared=true",
                        info.equipmentId(),
                        deviceId);
            }

            return new HsmsEquipmentRuntimeContext(
                    profile,
                    inboundQueue,
                    reassemblyBuffer,
                    tags,
                    sessionStateMachine
            );
        }

        // SOCKET은 socketType별 extractor/codec 분기를 위해 설정 객체와 registry를 함께 주입합니다.
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

        return new SocketEquipmentRuntimeContext(
                profile,
                inboundQueue,
                reassemblyBuffer,
                tags,
                socketTypeConfig,
                socketTypeRegistry
        );
    }
}
