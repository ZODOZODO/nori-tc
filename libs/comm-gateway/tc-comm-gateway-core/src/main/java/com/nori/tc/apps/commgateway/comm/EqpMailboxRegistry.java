package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.apps.commgateway.config.GatewayRuntimeProperties;
import com.nori.tc.apps.commgateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * eqpId -> EqpMailbox registry.
 *
 * - Mailbox는 BOUND 이후 생성됩니다.
 * - 연결 종료 시 mailbox 제거를 권장합니다.
 */
public final class EqpMailboxRegistry {

    private final EquipmentContextFactory contextFactory;
    private final GatewayRuntimeProperties runtimeProperties;
    private final Map<String, EqpMailbox> mailboxes = new ConcurrentHashMap<>();

    public EqpMailboxRegistry(
            final EquipmentContextFactory contextFactory,
            final GatewayRuntimeProperties runtimeProperties
    ) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");
    }

    public EqpMailbox get(final String eqpId) {
        return mailboxes.get(eqpId);
    }

    public EqpMailbox createAndBind(final GatewayEquipmentInfo info, final EquipmentChannel channel) {
        Objects.requireNonNull(info, "info is null");
        Objects.requireNonNull(channel, "channel is null");

        final String eqpId = info.equipmentId();
        final BoundedInboundQueue inboundQueue = new BoundedInboundQueue(runtimeProperties.getInboundQueueCapacity());
        final EquipmentRuntimeContext ctx = contextFactory.create(info, inboundQueue);
        final BoundedOutboundQueue outboundQueue = new BoundedOutboundQueue(runtimeProperties.getOutboundQueueCapacity());

        final EqpMailbox mailbox = new EqpMailbox(
                eqpId,
                info.commInterfaceType(),
                ctx,
                inboundQueue,
                outboundQueue
        );

        mailbox.bindChannel(channel);

        final EqpMailbox existing = mailboxes.putIfAbsent(eqpId, mailbox);
        if (existing != null) {
            throw new IllegalStateException("Mailbox already exists for eqpId=" + eqpId);
        }

        return mailbox;
    }

    public void remove(final String eqpId) {
        if (eqpId == null) {
            return;
        }
        mailboxes.remove(eqpId);
    }
}
