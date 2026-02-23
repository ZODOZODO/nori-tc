package com.nori.tc.comm.gateway.application.ingress;

import com.nori.tc.comm.gateway.application.processing.EquipmentProcessingCoordinator;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.dlq.DlqMessage;
import com.nori.tc.comm.gateway.domain.dlq.DlqReasonCode;
import com.nori.tc.comm.gateway.equipment.port.EquipmentInfoProvider;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogSampler;
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import com.nori.tc.comm.gateway.runtime.channel.EquipmentChannel;
import com.nori.tc.comm.gateway.runtime.mailbox.EquipmentMailbox;
import com.nori.tc.comm.gateway.runtime.mailbox.EquipmentMailboxRegistry;
import com.nori.tc.comm.gateway.runtime.mailbox.OutboundQueueCommand;
import com.nori.tc.comm.core.inbound.InboundChunk;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 게이트웨이 인입/출력 큐 적재 진입 서비스입니다.
 *
 * <p>이 클래스는 "실제 프로토콜 처리"를 수행하지 않고, 외부 어댑터(Netty/Kafka 등)에서 들어온 요청을
 * 설비별 {@link EquipmentMailbox}에 적재하고 {@link EquipmentProcessingCoordinator}에 스케줄링 요청만 전달합니다.</p>
 *
 * <p>주요 책임:</p>
 * <p>1) inbound raw bytes를 설비별 inbound queue에 적재</p>
 * <p>2) outbound frame을 설비별 outbound queue에 적재</p>
 * <p>3) 큐 overflow 시 DLQ/격리/채널 종료 정책 호출</p>
 * <p>4) BOUND/UNBOUND 시점 mailbox 생성/제거 조율</p>
 */
@Service
public class GatewayIngressService {

    private static final Logger log = LoggerFactory.getLogger(GatewayIngressService.class);

    // DB/캐시 등에서 설비 정보를 조회하는 포트(어댑터 구현체가 주입됨)
    private final EquipmentInfoProvider equipmentInfoProvider;
    private final EquipmentMailboxRegistry mailboxRegistry;
    private final EquipmentProcessingCoordinator processingCoordinator;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;
    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;
    private final DlqPublisherPort dlqPublisherPort;
    private final QuarantinePort quarantinePort;

    
    /**
     * 인입/출력 큐 적재 서비스 의존성을 초기화합니다.
     *
     * @param equipmentInfoProvider 설비 메타 정보 조회 포트(DB/캐시 어댑터 구현체)
     * @param mailboxRegistry 설비별 mailbox 생성/조회 레지스트리
     * @param processingCoordinator 설비별 순차 처리 스케줄링 코디네이터
     * @param metrics 큐 깊이/overflow 계측 컴포넌트
     * @param logSampler 과도한 경고 로그 억제를 위한 샘플러
     * @param clockPort 현재 시각(epoch millis) 제공 포트
     * @param traceIdGeneratorPort DLQ 추적용 traceId 생성 포트
     * @param dlqPublisherPort DLQ 발행 포트
     * @param quarantinePort 설비 격리 포트
     */
    public GatewayIngressService(
            final EquipmentInfoProvider equipmentInfoProvider,
            final EquipmentMailboxRegistry mailboxRegistry,
            final EquipmentProcessingCoordinator processingCoordinator,
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

        log.info("GatewayIngressService initialized. mailboxQueueingEnabled=true");
    }

    /**
     * 외부 어댑터(Netty 등)에서 수신한 raw payload를 설비별 inbound 큐에 적재합니다.
     *
     * <p>이 메서드는 실제 파싱/비즈니스 처리를 수행하지 않습니다.
     * 큐 적재 성공 시 {@link EquipmentProcessingCoordinator}에 스케줄링만 요청합니다.</p>
     *
     * @param equipmentId 설비 ID
     * @param payload 수신 원본 바이트 배열
     */
    public void enqueueInbound(final String equipmentId, final byte[] payload) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(payload, "payload is null");

        final EquipmentMailbox mailbox = mailboxRegistry.get(equipmentId);
        if (mailbox == null) {
            // UNBOUND 또는 소유하지 않은 eqp -> drop
            if (log.isDebugEnabled()) {
                log.debug("Inbound drop (no mailbox). eqpId={}", equipmentId);
            }
            return;
        }

        // 수신 시각을 함께 저장해 후속 처리 지연/timeout 계산에 활용합니다.
        final boolean offered = mailbox.inboundQueue()
                .offer(new InboundChunk(payload, clockPort.nowEpochMillis()));

        metrics.recordInboundQueueDepth(equipmentId, mailbox.inboundQueue().size());

        if (!offered) {
            metrics.incrementInboundQueueOverflow();
            if (logSampler.shouldLogQueueOverflow()) {
                // overflow는 burst 상황에서 연속 발생할 수 있으므로 핵심 정보만 경고로 남깁니다.
                log.warn("Inbound queue overflow. eqpId={}", equipmentId);
            }
            // overflow 후속 정책(DLQ/격리)은 별도 메서드에서 일괄 처리합니다.
            handleQueueOverflow(mailbox, payload);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Inbound queued. eqpId={}, queueDepth={}", equipmentId, mailbox.inboundQueue().size());
        }
        processingCoordinator.schedule(mailbox);
    }

    /**
     * Kafka 등 외부 채널에서 들어온 outbound 프레임을 설비별 outbound 큐에 적재합니다.
     *
     * <p>실제 전송은 여기서 하지 않으며, worker 스레드가 {@link EquipmentProcessingCoordinator}에서
     * 순차적으로 꺼내서 전송합니다.</p>
     *
     * @param frame 전송 대상 raw frame
     */
    public void enqueueOutbound(final OutboundRawFrame frame) {
        Objects.requireNonNull(frame, "frame is null");

        final String eqpId = frame.equipmentId().value();
        final EquipmentMailbox mailbox = mailboxRegistry.get(eqpId);
        if (mailbox == null) {
            // 연결 없으면 drop
            if (log.isDebugEnabled()) {
                log.debug("Outbound drop (no mailbox). eqpId={}", eqpId);
            }
            return;
        }

        // attempt=0 으로 시작하며 재시도 시 coordinator가 증가시킵니다.
        final boolean offered = mailbox.outboundQueue()
                .offer(new OutboundQueueCommand(frame, 0, clockPort.nowEpochMillis()));
        metrics.recordOutboundQueueDepth(eqpId, mailbox.outboundQueue().size());
        if (!offered) {
            metrics.incrementOutboundQueueOverflow();
            if (logSampler.shouldLogQueueOverflow()) {
                log.warn("Outbound queue overflow. eqpId={}", eqpId);
            }
            // outbound overflow는 송신 지연이 누적된 상태일 수 있으므로 격리 후 채널 종료로 회복을 유도합니다.
            safeQuarantine(mailbox, "Outbound queue overflow");
            final EquipmentChannel channel = mailbox.channel();
            if (channel != null) {
                channel.close();
            }
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Outbound queued. eqpId={}, queueDepth={}", eqpId, mailbox.outboundQueue().size());
        }
        processingCoordinator.schedule(mailbox);
    }

    /**
     * BOUND 시점에 설비 mailbox를 생성하고 채널을 바인딩합니다.
     *
     * <p>실제 mailbox 생성/중복 검사/런타임 컨텍스트 생성은 {@link EquipmentMailboxRegistry}가 담당하고,
     * 본 메서드는 입력 검증과 호출 흐름 제어에 집중합니다.</p>
     *
     * @param info 설비 메타 정보(DB 조회 결과)
     * @param channel 새로 연결된 설비 채널
     * @return 생성 및 바인딩이 완료된 설비 mailbox
     */
    public EquipmentMailbox bindMailbox(final GatewayEquipmentInfo info, final EquipmentChannel channel) {
        // 연결 제어 단계에서 잘못된 메타 정보로 mailbox가 생성되지 않도록 선제 검증합니다.
        if (info == null || !info.enabled()) {
            throw new IllegalArgumentException("Invalid equipment info");
        }
        Objects.requireNonNull(channel, "channel is null");
        if (log.isDebugEnabled()) {
            log.debug("Binding mailbox. eqpId={}, interfaceType={}", info.equipmentId(), info.commInterfaceType());
        }
        final EquipmentMailbox mailbox = mailboxRegistry.createAndBind(info, channel);
        if (log.isDebugEnabled()) {
            log.debug("Mailbox binding completed. eqpId={}", info.equipmentId());
        }
        return mailbox;
    }

    /**
     * UNBOUND/종료 시점에 설비 mailbox 및 관련 스케줄링/메트릭 상태를 정리합니다.
     *
     * <p>정리 대상:</p>
     * <p>1) mailbox registry 항목</p>
     * <p>2) mailbox scheduler dedup 상태</p>
     * <p>3) 큐 깊이 메트릭 캐시</p>
     *
     * @param equipmentId 설비 ID
     */
    public void removeMailbox(final String equipmentId) {
        mailboxRegistry.remove(equipmentId);
        processingCoordinator.clearSchedulingState(equipmentId);
        metrics.clearQueueDepth(equipmentId);
        if (log.isDebugEnabled()) {
            log.debug("Mailbox removed via processingService. eqpId={}", equipmentId);
        }
    }

    /**
     * 설비 ID로 메타 정보를 조회합니다.
     *
     * <p>BOUND/제어 이벤트 처리에서 사용되며, 설비가 없으면 즉시 예외를 발생시켜 상위에서 실패로 처리합니다.</p>
     *
     * @param equipmentId 설비 ID
     * @return 조회된 설비 메타 정보
     */
    public GatewayEquipmentInfo resolveEquipment(final String equipmentId) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");

        return equipmentInfoProvider.findById(equipmentId).orElseThrow(
                () -> new IllegalArgumentException("No equipment found for eqpId=" + equipmentId)
        );
    }

    /**
     * inbound 큐 overflow 후속 조치(DLQ 발행 + 격리)를 수행합니다.
     *
     * <p>핵심 정책:</p>
     * <p>- 원본 payload는 DLQ에 기록해 사후 분석 가능성을 확보</p>
     * <p>- 설비는 quarantine 포트로 격리 요청</p>
     * <p>- DLQ/격리 실패는 본 흐름의 추가 예외로 전파하지 않음(운영 로그로만 남김)</p>
     *
     * @param mailbox overflow가 발생한 설비 mailbox
     * @param payload overflow를 유발한 원본 payload
     */
    private void handleQueueOverflow(final EquipmentMailbox mailbox, final byte[] payload) {
        Objects.requireNonNull(mailbox, "mailbox is null");
        Objects.requireNonNull(payload, "payload is null");

        // 동일 overflow 흐름에서 사용할 시각/추적 ID를 먼저 확보합니다.
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
            log.warn("DLQ publish failed after inbound overflow. eqpId={}",
                    mailbox.context().profile().equipmentId().value(), ignored);
        }

        try {
            quarantinePort.quarantine(
                    mailbox.context().profile().equipmentId(),
                    DlqReasonCode.INBOUND_QUEUE_OVERFLOW.name(),
                    "Inbound queue overflow"
            );
        } catch (Exception ignored) {
            log.warn("Quarantine failed after inbound overflow. eqpId={}",
                    mailbox.context().profile().equipmentId().value(), ignored);
        }
    }

    /**
     * outbound 큐 overflow 등 송신 계열 실패 시 설비 격리 요청을 안전하게 수행합니다.
     *
     * <p>격리 포트 실패는 본 메서드에서 swallow 하되 운영 로그로 남깁니다.</p>
     *
     * @param mailbox 격리 대상 설비 mailbox
     * @param reason 격리 사유(운영 추적용 문자열)
     */
    private void safeQuarantine(final EquipmentMailbox mailbox, final String reason) {
        Objects.requireNonNull(mailbox, "mailbox is null");
        Objects.requireNonNull(reason, "reason is null");

        try {
            quarantinePort.quarantine(
                    mailbox.context().profile().equipmentId(),
                    "OUTBOUND_QUEUE_OVERFLOW",
                    reason
            );
        } catch (Exception ignored) {
            log.warn("Quarantine failed for outbound path. eqpId={}, reason={}",
                    mailbox.eqpId(), reason, ignored);
        }
    }
}
