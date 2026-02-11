package com.nori.tc.comm.core.usecase;

import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.inbound.InboundChunk;
import com.nori.tc.comm.core.message.InboundProcessResult;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.domain.dlq.DlqMessage;
import com.nori.tc.comm.domain.dlq.DlqReasonCode;
import com.nori.tc.comm.domain.type.CommInterfaceType;

import java.util.Map;
import java.util.Objects;

/**
 * 설비(eqpid) 단위 순차 처리 유스케이스 (FIFO + in-flight=1)
 *
 * 핵심 목표
 * - eqp별 메시지 순차성 보장
 * - 채널 스레드(Netty EventLoop)에서 파싱/세션 처리 금지 → 여기서 처리
 * - 장애/비정상 입력 발생 시 DLQ + Quarantine로 전체 영향 최소화
 *
 * 처리 흐름(요약)
 * 1) inboundQueue에서 chunk poll
 * 2) reassemblyBuffer.append(chunk)
 * 3) inboundPipeline.drain(ctx)로 가능한 만큼 메시지/아웃바운드 프레임 생성
 * 4) outboundFrames는 OutboundSenderPort로 즉시 송신
 * 5) parsedMessages는 RouteAndPublishUseCase로 라우팅/발행
 *
 * 운영 안전 장치(권장)
 * - drain은 배치 제한(maxChunksPerDrain)을 둬서 한 설비가 CPU를 독점하지 않게 합니다.
 * - 예외 발생 시:
 *   - DLQ 기록(표준 메타)
 *   - 설비 quarantine(격리)
 *   - reassembly buffer clear(안전 우선)
 */
public final class EqpSequentialProcessor {

    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;

    private final InboundPipelinePort inboundPipelinePort;
    private final OutboundSenderPort outboundSenderPort;

    private final RouteAndPublishUseCase routeAndPublishUseCase;

    private final DlqPublisherPort dlqPublisherPort;
    private final QuarantinePort quarantinePort;

    /**
     * 한 번 drain 호출 시, 큐에서 최대 몇 개 chunk를 처리할지(공정성/지연 제어)
     */
    private final int maxChunksPerDrain;

    public EqpSequentialProcessor(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final InboundPipelinePort inboundPipelinePort,
            final OutboundSenderPort outboundSenderPort,
            final RouteAndPublishUseCase routeAndPublishUseCase,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort,
            final int maxChunksPerDrain
    ) {
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceIdGeneratorPort = Objects.requireNonNull(traceIdGeneratorPort, "traceIdGeneratorPort is null");
        this.inboundPipelinePort = Objects.requireNonNull(inboundPipelinePort, "inboundPipelinePort is null");
        this.outboundSenderPort = Objects.requireNonNull(outboundSenderPort, "outboundSenderPort is null");
        this.routeAndPublishUseCase = Objects.requireNonNull(routeAndPublishUseCase, "routeAndPublishUseCase is null");
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
        this.quarantinePort = Objects.requireNonNull(quarantinePort, "quarantinePort is null");

        if (maxChunksPerDrain <= 0) {
            throw new IllegalArgumentException("maxChunksPerDrain must be > 0");
        }
        this.maxChunksPerDrain = maxChunksPerDrain;
    }

    /**
     * eqp 컨텍스트에 대해 "가능한 만큼" 순차 처리합니다.
     *
     * 호출 방식(권장)
     * - 앱(KeyedExecutor 등)에서 eqpId별로 이 메서드를 호출하여,
     *   한 번에 너무 오래 점유하지 않도록 배치 제한(maxChunksPerDrain)을 적용합니다.
     */
    public void drain(final EquipmentRuntimeContext ctx) {
        Objects.requireNonNull(ctx, "ctx is null");

        int processedChunks = 0;

        while (processedChunks < maxChunksPerDrain) {
            final InboundChunk chunk = ctx.inboundQueue().poll();
            if (chunk == null) {
                return; // 더 이상 처리할 chunk 없음
            }

            processedChunks++;

            try {
                // 1) chunk 누적
                ctx.reassemblyBuffer().append(chunk.bytes());

                // 2) 누적 버퍼에서 가능한 만큼 프레임/메시지 drain
                final InboundProcessResult result = inboundPipelinePort.drain(ctx);

                // 3) outbounds 먼저 송신(세션 유지/응답 요구에 도움)
                for (OutboundRawFrame frame : result.outboundFrames()) {
                    outboundSenderPort.send(frame);
                }

                // 4) parsed messages 라우팅/발행
                for (ParsedMessage message : result.parsedMessages()) {
                    routeAndPublishUseCase.routeAndPublish(message);
                }

            } catch (Exception ex) {
                // 한 설비 문제를 전체로 번지지 않게: DLQ + Quarantine
                handleFailure(ctx, chunk, ex);
                return;
            }
        }
    }

    // -------------------------
    // internal
    // -------------------------

    private void handleFailure(final EquipmentRuntimeContext ctx, final InboundChunk chunk, final Exception ex) {
        final long now = clockPort.nowEpochMillis();
        final EquipmentProfile profile = ctx.profile();

        final String traceId = traceIdGeneratorPort.newTraceId();
        final CommInterfaceType commInterfaceType = profile.commInterfaceType();
        final String socketType = profile.socketType();

        // 현재는 안전하게 "PARSING_FAILED"로 대표 처리(가장 흔한 실패 지점)
        // 실제 운영에서 원하면 예외 타입별로 더 정교하게 분류하십시오.
        final DlqReasonCode reasonCode = DlqReasonCode.PARSING_FAILED;

        final DlqMessage dlqMessage = new DlqMessage(
                traceIdGeneratorPort.newTraceId(),          // dlqId
                profile.equipmentId().value(),              // eqpId
                traceId,                                    // traceId
                commInterfaceType,                          // HSMS/SOCKET
                socketType,                                 // socketType (SOCKET만 의미)
                DlqMessage.STAGE_PARSING,                   // stage
                reasonCode,                                 // reason
                safeMessage(ex),                            // reasonMessage
                now,                                        // occurredAt
                null,                                       // payloadRefKey(저장소 전략은 app에서)
                DlqMessage.UNKNOWN_LENGTH,                  // rawLen
                DlqMessage.UNKNOWN_LENGTH,                  // b64Len
                ctx.tags() == null ? Map.of() : ctx.tags()   // tags
        );

        try {
            dlqPublisherPort.publish(dlqMessage);
        } catch (Exception dlqEx) {
            // DLQ 발행 실패는 반드시 운영 관측 대상입니다.
            // core 엔진은 여기서 예외를 재던지지 않습니다(전체 흔들림 방지).
        }

        try {
            quarantinePort.quarantine(profile.equipmentId(), reasonCode.name(), safeMessage(ex));
        } catch (Exception qEx) {
            // 격리 실패 역시 운영 관측 대상입니다.
        }

        // 안전 우선: 잘못된 데이터로 계속 실패하지 않도록 버퍼를 비웁니다.
        ctx.reassemblyBuffer().clear();
    }

    private static String safeMessage(final Exception ex) {
        final String msg = ex.getMessage();
        if (msg == null) return ex.getClass().getSimpleName();

        // 운영 로그 폭주 방지: 메시지를 너무 길게 싣지 않습니다.
        final int limit = 300;
        return msg.length() <= limit ? msg : msg.substring(0, limit) + "...";
    }
}
