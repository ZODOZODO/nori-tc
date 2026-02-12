package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.domain.type.CommInterfaceType;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * eqpId 단위 런타임 mailbox.
 *
 * - inbound/outbound 큐
 * - 스케줄링 플래그(scheduled)
 * - in-flight 제어 플래그(inFlight)
 */
/**
 * Per-equipment mailbox.
 *
 * Responsibilities:
 * - Hold bounded inbound/outbound queues for a single eqpId.
 * - Track scheduling/in-flight flags to enforce sequential processing.
 * - Keep a reference to the active channel for outbound writes.
 *
 * Notes:
 * - inboundQueue is shared with the runtime context so parsing sees the same data.
 * - scheduled/inFlight are used by EqpProcessingCoordinator to avoid duplicate work.
 */
public final class EqpMailbox {

    private final String eqpId;
    private final CommInterfaceType commInterfaceType;
    private final EquipmentRuntimeContext context;
    private final BoundedInboundQueue inboundQueue;
    private final BoundedOutboundQueue outboundQueue;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    private volatile EquipmentChannel channel;

    public EqpMailbox(
            final String eqpId,
            final CommInterfaceType commInterfaceType,
            final EquipmentRuntimeContext context,
            final BoundedInboundQueue inboundQueue,
            final BoundedOutboundQueue outboundQueue
    ) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        this.eqpId = eqpId;
        this.commInterfaceType = Objects.requireNonNull(commInterfaceType, "commInterfaceType is null");
        this.context = Objects.requireNonNull(context, "context is null");
        this.inboundQueue = Objects.requireNonNull(inboundQueue, "inboundQueue is null");
        this.outboundQueue = Objects.requireNonNull(outboundQueue, "outboundQueue is null");
    }

    public String eqpId() {
        return eqpId;
    }

    public CommInterfaceType commInterfaceType() {
        return commInterfaceType;
    }

    public EquipmentRuntimeContext context() {
        return context;
    }

    /**
     * Bounded inbound queue for this equipment.
     *
     * This queue is shared with the runtime context so that the sequential
     * processor and enqueue path see the same data structure.
     */
    public BoundedInboundQueue inboundQueue() {
        return inboundQueue;
    }

    public BoundedOutboundQueue outboundQueue() {
        return outboundQueue;
    }

    public EquipmentChannel channel() {
        return channel;
    }

    public void bindChannel(final EquipmentChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel is null");
    }

    public void clearChannel() {
        this.channel = null;
    }

    public AtomicBoolean scheduledFlag() {
        return scheduled;
    }

    public AtomicBoolean inFlightFlag() {
        return inFlight;
    }
}
