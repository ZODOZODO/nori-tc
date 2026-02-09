package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.apps.commgateway.db.GatewayEquipmentService;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.inbound.InboundChunk;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceNoGeneratorPort;
import com.nori.tc.comm.core.usecase.EqpSequentialProcessor;
import com.nori.tc.comm.domain.dlq.DlqMessage;
import com.nori.tc.comm.domain.dlq.DlqReasonCode;
import com.nori.tc.db.jpa.site.gateway.GatewayEquipmentEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gateway 순차 처리 진입점
 *
 * 역할
 * - Netty 수신 raw bytes를 eqp별 inbound 큐에 넣습니다.
 * - eqp별 순차 처리(격리)를 위해 PerEquipmentExecutor로 drain 작업을 스케줄링합니다.
 */
@Service
public class GatewayProcessingService {

    private final GatewayEquipmentService equipmentService;
    private final EquipmentContextFactory contextFactory;
    private final EqpSequentialProcessor sequentialProcessor;
    private final PerEquipmentExecutor perEquipmentExecutor;
    private final ClockPort clockPort;
    private final TraceNoGeneratorPort traceNoGeneratorPort;
    private final DlqPublisherPort dlqPublisherPort;
    private final QuarantinePort quarantinePort;

    private final Map<String, EquipmentRuntimeContext> contexts = new ConcurrentHashMap<>();

    public GatewayProcessingService(
            final GatewayEquipmentService equipmentService,
            final EquipmentContextFactory contextFactory,
            final EqpSequentialProcessor sequentialProcessor,
            final PerEquipmentExecutor perEquipmentExecutor,
            final ClockPort clockPort,
            final TraceNoGeneratorPort traceNoGeneratorPort,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort
    ) {
        this.equipmentService = Objects.requireNonNull(equipmentService, "equipmentService is null");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory is null");
        this.sequentialProcessor = Objects.requireNonNull(sequentialProcessor, "sequentialProcessor is null");
        this.perEquipmentExecutor = Objects.requireNonNull(perEquipmentExecutor, "perEquipmentExecutor is null");
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceNoGeneratorPort = Objects.requireNonNull(traceNoGeneratorPort, "traceNoGeneratorPort is null");
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

        final EquipmentRuntimeContext ctx = getOrCreateContext(equipmentId);
        final boolean offered = ctx.inboundQueue().offer(new InboundChunk(payload, clockPort.nowEpochMillis()));

        if (!offered) {
            handleQueueOverflow(ctx, payload);
            return;
        }

        // eqp별 순차 처리 스케줄링
        perEquipmentExecutor.execute(equipmentId, () -> sequentialProcessor.drain(ctx));
    }

    /**
     * eqp 컨텍스트를 미리 등록하고 싶을 때 사용합니다.
     */
    public void register(final GatewayEquipmentEntity entity) {
        if (entity == null || !entity.isEnabled()) {
            return;
        }
        contexts.put(entity.getEquipmentId(), contextFactory.create(entity));
    }

    private EquipmentRuntimeContext getOrCreateContext(final String equipmentId) {
        final EquipmentRuntimeContext cached = contexts.get(equipmentId);
        if (cached != null) {
            return cached;
        }

        final Optional<GatewayEquipmentEntity> entity = equipmentService.findById(equipmentId);
        final GatewayEquipmentEntity resolved = entity.orElseThrow(
                () -> new IllegalArgumentException("No equipment found for eqpId=" + equipmentId)
        );

        final EquipmentRuntimeContext created = contextFactory.create(resolved);
        contexts.put(equipmentId, created);
        return created;
    }

    private void handleQueueOverflow(final EquipmentRuntimeContext ctx, final byte[] payload) {
        final long now = clockPort.nowEpochMillis();
        final String traceNo = traceNoGeneratorPort.newTraceNo();

        final DlqMessage dlqMessage = new DlqMessage(
                traceNoGeneratorPort.newTraceNo(),
                ctx.profile().equipmentId().value(),
                traceNo,
                ctx.profile().commInterfaceType(),
                ctx.profile().socketType(),
                DlqMessage.STAGE_ENQUEUE,
                DlqReasonCode.INBOUND_QUEUE_OVERFLOW,
                "Inbound queue overflow",
                now,
                null,
                payload.length,
                DlqMessage.UNKNOWN_LENGTH,
                ctx.tags()
        );

        try {
            dlqPublisherPort.publish(dlqMessage);
        } catch (Exception ignored) {
            // DLQ 실패는 운영에서 모니터링합니다.
        }

        try {
            quarantinePort.quarantine(ctx.profile().equipmentId(), DlqReasonCode.INBOUND_QUEUE_OVERFLOW.name(), "Inbound queue overflow");
        } catch (Exception ignored) {
        }
    }
}
