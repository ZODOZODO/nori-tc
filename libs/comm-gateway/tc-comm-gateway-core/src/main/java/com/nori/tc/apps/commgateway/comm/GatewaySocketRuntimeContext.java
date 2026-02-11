package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.inbound.InboundQueue;
import com.nori.tc.comm.socket.config.SocketTypeConfig;
import com.nori.tc.comm.socket.pipeline.SocketRuntimeContext;
import com.nori.tc.comm.socket.socketType.SocketTypeRegistry;

import java.util.Map;
import java.util.Objects;

/**
 * SOCKET 설비용 런타임 컨텍스트
 */
public final class GatewaySocketRuntimeContext implements SocketRuntimeContext {

    private final EquipmentProfile profile;
    private final InboundQueue inboundQueue;
    private final ReassemblyBuffer reassemblyBuffer;
    private final Map<String, String> tags;
    private final SocketTypeConfig socketTypeConfig;
    private final SocketTypeRegistry socketTypeRegistry;

    public GatewaySocketRuntimeContext(
            final EquipmentProfile profile,
            final InboundQueue inboundQueue,
            final ReassemblyBuffer reassemblyBuffer,
            final Map<String, String> tags,
            final SocketTypeConfig socketTypeConfig,
            final SocketTypeRegistry socketTypeRegistry
    ) {
        this.profile = Objects.requireNonNull(profile, "profile is null");
        this.inboundQueue = Objects.requireNonNull(inboundQueue, "inboundQueue is null");
        this.reassemblyBuffer = Objects.requireNonNull(reassemblyBuffer, "reassemblyBuffer is null");
        this.tags = (tags == null) ? Map.of() : Map.copyOf(tags);
        this.socketTypeConfig = Objects.requireNonNull(socketTypeConfig, "socketTypeConfig is null");
        this.socketTypeRegistry = Objects.requireNonNull(socketTypeRegistry, "socketTypeRegistry is null");
    }

    @Override
    public EquipmentProfile profile() {
        return profile;
    }

    @Override
    public InboundQueue inboundQueue() {
        return inboundQueue;
    }

    @Override
    public ReassemblyBuffer reassemblyBuffer() {
        return reassemblyBuffer;
    }

    @Override
    public Map<String, String> tags() {
        return tags;
    }

    @Override
    public SocketTypeConfig socketTypeConfig() {
        return socketTypeConfig;
    }

    @Override
    public SocketTypeRegistry socketTypeRegistry() {
        return socketTypeRegistry;
    }
}
