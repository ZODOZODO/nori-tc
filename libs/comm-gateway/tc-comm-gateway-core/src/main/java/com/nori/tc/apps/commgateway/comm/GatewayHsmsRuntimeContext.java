package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.inbound.InboundQueue;
import com.nori.tc.comm.hsms.pipeline.HsmsRuntimeContext;
import com.nori.tc.comm.hsms.session.HsmsSessionStateMachine;

import java.util.Map;
import java.util.Objects;

/**
 * HSMS 설비용 런타임 컨텍스트
 */
public final class GatewayHsmsRuntimeContext implements HsmsRuntimeContext {

    private final EquipmentProfile profile;
    private final InboundQueue inboundQueue;
    private final ReassemblyBuffer reassemblyBuffer;
    private final Map<String, String> tags;
    private final HsmsSessionStateMachine hsmsSession;

    public GatewayHsmsRuntimeContext(
            final EquipmentProfile profile,
            final InboundQueue inboundQueue,
            final ReassemblyBuffer reassemblyBuffer,
            final Map<String, String> tags,
            final HsmsSessionStateMachine hsmsSession
    ) {
        this.profile = Objects.requireNonNull(profile, "profile is null");
        this.inboundQueue = Objects.requireNonNull(inboundQueue, "inboundQueue is null");
        this.reassemblyBuffer = Objects.requireNonNull(reassemblyBuffer, "reassemblyBuffer is null");
        this.tags = (tags == null) ? Map.of() : Map.copyOf(tags);
        this.hsmsSession = Objects.requireNonNull(hsmsSession, "hsmsSession is null");
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
    public HsmsSessionStateMachine hsmsSession() {
        return hsmsSession;
    }
}
