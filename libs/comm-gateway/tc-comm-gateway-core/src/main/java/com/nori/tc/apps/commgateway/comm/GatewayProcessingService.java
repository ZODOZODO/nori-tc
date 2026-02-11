package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.apps.commgateway.db.GatewayEquipmentInfo;
import com.nori.tc.apps.commgateway.metrics.GatewayLogSampler;
import com.nori.tc.apps.commgateway.metrics.GatewayMetrics;
import com.nori.tc.comm.core.inbound.InboundChunk;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.domain.dlq.DlqMessage;
import com.nori.tc.comm.domain.dlq.DlqReasonCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * gateway 처리 진입점
 *
 * 역할
 * - Netty 수신 raw bytes를 eqp별 inbound 큐에 적재합니다.
 * - eqp별 순차 처리(격리)를 위해 ReadyQueue/Worker에 스케줄링합니다.
 */
@Service
public class GatewayProcessingService {

    private static final Logger log = LoggerFactory.getLogger(GatewayProcessingService.class);

    // DB/캐시 등에서 설비 정보를 조회하는 포트(어댑터 구현체가 주입됨)
    private final EquipmentInfoProvider equipmentInfoProvider;
    private final EqpMailboxRegistry mailboxRegistry;
    private final EqpProcessingCoordinator processingCoordinator;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;
    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;
    private final DlqPublisherPort dlqPublisherPort;
    private final QuarantinePort quarantinePort;

    public GatewayProcessingService(
            final EquipmentInfoProvider equipmentInfoProvider,
            final EqpMailboxRegistry mailboxRegistry,
            final EqpProcessingCoordinator processingCoordinator,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler,
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort
    ) {
        this.equipmentInfoProvider = Objects.requireNonNull(equipmentInfoProvider, "equipmentInfoProvider is null");
        this.mailboxRegistry = Objects.requireNonNull(mailboxRegistry, "mailboxRegistry is null");
        this.processingCoordinator = Objects.requireNonNull(processingCoordinator, "processingCoordinator is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceIdGeneratorPort = Objects.requireNonNull(traceIdGeneratorPort, "traceIdGeneratorPort is null");
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
        this.quarantinePort = Objects.requireNonNull(quarantinePort, "quarantinePort is null");
    }

    /**
     * 외부(Netty 등)에서 수신된 raw bytes를 eqp별 큐에 적재합니다.
     *
     * @param equipmentId 설비 ID
     * @param payload     raw bytes
     */
    public void enqueueInbound(final String equipmentId, final byte[] payload) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(payload, "payload is null");

        final EqpMailbox mailbox = mailboxRegistry.get(equipmentId);
        if (mailbox == null) {
            // UNBOUND 또는 소유하지 않은 eqp -> drop
            if (log.isDebugEnabled()) {
                log.debug("Inbound drop (no mailbox). eqpId={}", equipmentId);
            }
            return;
        }

        final boolean offered = mailbox.inboundQueue()
                .offer(new InboundChunk(payload, clockPort.nowEpochMillis()));

        metrics.recordInboundQueueDepth(equipmentId, mailbox.inboundQueue().size());

        if (!offered) {
            metrics.incrementInboundQueueOverflow();
            if (logSampler.shouldLogQueueOverflow()) {
                // inbound overflow -> close/quarantine handled in handleQueueOverflow
                // keep log concise to avoid flooding
                log.warn("Inbound queue overflow. eqpId={}", equipmentId);
            }
            handleQueueOverflow(mailbox, payload);
            return;
        }

        processingCoordinator.schedule(mailbox);
    }

    /**
     * Kafka command 등 outbound 요청을 큐에 적재합니다.
     */
    /**
     * Kafka command -> outbound queue.
     *
     * - Queue-based outbound: enqueue only.
     * - Actual send is done by worker threads in EqpProcessingCoordinator.
     */
    public void enqueueOutbound(final OutboundRawFrame frame) {
        Objects.requireNonNull(frame, "frame is null");

        final String eqpId = frame.equipmentId().value();
        final EqpMailbox mailbox = mailboxRegistry.get(eqpId);
        if (mailbox == null) {
            // 연결 없으면 drop
            if (log.isDebugEnabled()) {
                log.debug("Outbound drop (no mailbox). eqpId={}", eqpId);
            }
            return;
        }

        final boolean offered = mailbox.outboundQueue().offer(new OutboundCommand(frame, 0, clockPort.nowEpochMillis()));
        metrics.recordOutboundQueueDepth(eqpId, mailbox.outboundQueue().size());
        if (!offered) {
            metrics.incrementOutboundQueueOverflow();
            if (logSampler.shouldLogQueueOverflow()) {
                log.warn("Outbound queue overflow. eqpId={}", eqpId);
            }
            safeQuarantine(mailbox, "Outbound queue overflow");
            final EquipmentChannel channel = mailbox.channel();
            if (channel != null) {
                channel.close();
            }
            return;
        }

        processingCoordinator.schedule(mailbox);
    }

    /**
     * BOUND 시점에서 mailbox를 생성/등록합니다.
     */
    public EqpMailbox bindMailbox(final GatewayEquipmentInfo info, final EquipmentChannel channel) {
        if (info == null || !info.enabled()) {
            throw new IllegalArgumentException("Invalid equipment info");
        }
        if (log.isDebugEnabled()) {
            log.debug("Binding mailbox. eqpId={}, interfaceType={}", info.equipmentId(), info.commInterfaceType());
        }
        return mailboxRegistry.createAndBind(info, channel);
    }

    public void removeMailbox(final String equipmentId) {
        mailboxRegistry.remove(equipmentId);
        metrics.clearQueueDepth(equipmentId);
        if (log.isDebugEnabled()) {
            log.debug("Mailbox removed via processingService. eqpId={}", equipmentId);
        }
    }

    public GatewayEquipmentInfo resolveEquipment(final String equipmentId) {
        return equipmentInfoProvider.findById(equipmentId).orElseThrow(
                () -> new IllegalArgumentException("No equipment found for eqpId=" + equipmentId)
        );
    }

    private void handleQueueOverflow(final EqpMailbox mailbox, final byte[] payload) {
        final long now = clockPort.nowEpochMillis();
        final String traceId = traceIdGeneratorPort.newTraceId();

        final DlqMessage dlqMessage = new DlqMessage(
                traceIdGeneratorPort.newTraceId(),
                mailbox.context().profile().equipmentId().value(),
                traceId,
                mailbox.context().profile().commInterfaceType(),
                mailbox.context().profile().socketType(),
                DlqMessage.STAGE_ENQUEUE,
                DlqReasonCode.INBOUND_QUEUE_OVERFLOW,
                "Inbound queue overflow",
                now,
                null,
                payload.length,
                DlqMessage.UNKNOWN_LENGTH,
                mailbox.context().tags()
        );

        try {
            dlqPublisherPort.publish(dlqMessage);
        } catch (Exception ignored) {
            // DLQ 실패는 운영에서 모니터링합니다.
        }

        try {
            quarantinePort.quarantine(
                    mailbox.context().profile().equipmentId(),
                    DlqReasonCode.INBOUND_QUEUE_OVERFLOW.name(),
                    "Inbound queue overflow"
            );
        } catch (Exception ignored) {
        }
    }

    private void safeQuarantine(final EqpMailbox mailbox, final String reason) {
        try {
            quarantinePort.quarantine(
                    mailbox.context().profile().equipmentId(),
                    "OUTBOUND_QUEUE_OVERFLOW",
                    reason
            );
        } catch (Exception ignored) {
        }
    }
}
