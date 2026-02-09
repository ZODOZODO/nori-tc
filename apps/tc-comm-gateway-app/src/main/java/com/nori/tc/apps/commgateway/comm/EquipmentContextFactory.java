package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.apps.commgateway.config.GatewayHsmsProperties;
import com.nori.tc.apps.commgateway.config.GatewayPublishPolicyProperties;
import com.nori.tc.apps.commgateway.config.GatewayRuntimeProperties;
import com.nori.tc.apps.commgateway.config.GatewaySocketProperties;
import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.domain.type.CommInterfaceType;
import com.nori.tc.comm.hsms.config.HsmsSessionConfig;
import com.nori.tc.comm.hsms.session.HsmsSessionStateMachine;
import com.nori.tc.comm.socket.config.SocketTypeConfig;
import com.nori.tc.comm.socket.socketType.SocketTypeRegistry;
import com.nori.tc.db.jpa.site.gateway.GatewayEquipmentEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * eqp 프로파일(DB) + 설정(properties) -> 런타임 컨텍스트 생성
 */
@Component
public class EquipmentContextFactory {

    private final GatewayRuntimeProperties runtimeProperties;
    private final GatewayHsmsProperties hsmsProperties;
    private final GatewaySocketProperties socketProperties;
    private final GatewayPublishPolicyProperties publishPolicyProperties;
    private final SocketTypeRegistry socketTypeRegistry;

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

    public EquipmentRuntimeContext create(final GatewayEquipmentEntity entity) {
        Objects.requireNonNull(entity, "entity is null");

        final EquipmentId equipmentId = new EquipmentId(entity.getEquipmentId());
        final CommInterfaceType interfaceType = CommInterfaceType.fromText(entity.getCommInterfaceType());

        final String socketType = (entity.getSocketType() == null || entity.getSocketType().isBlank())
                ? socketProperties.getDefaultSocketType()
                : entity.getSocketType();

        final EquipmentProfile profile = new EquipmentProfile(
                equipmentId,
                interfaceType,
                interfaceType == CommInterfaceType.SOCKET ? socketType : null
        );

        final BoundedInboundQueue inboundQueue = new BoundedInboundQueue(runtimeProperties.getInboundQueueCapacity());
        final ReassemblyBuffer reassemblyBuffer = new ReassemblyBuffer(
                runtimeProperties.getReassemblyInitialBytes(),
                runtimeProperties.getReassemblyMaxBytes()
        );

        final Map<String, String> tags = Map.of(
                "policyVersion", publishPolicyProperties.getVersion(),
                "socketType", socketType
        );

        if (interfaceType == CommInterfaceType.HSMS) {
            final int deviceId = (entity.getHsmsDeviceId() == null)
                    ? hsmsProperties.getDeviceId()
                    : entity.getHsmsDeviceId();

            final HsmsSessionConfig sessionConfig = hsmsProperties.toSessionConfig(deviceId);
            final HsmsSessionStateMachine sessionStateMachine = new HsmsSessionStateMachine(sessionConfig);

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
