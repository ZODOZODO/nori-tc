package com.nori.tc.comm.core.usecase;

import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.inbound.InboundChunk;
import com.nori.tc.comm.core.message.InboundProcessResult;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.EventLogContextPort;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.gateway.domain.dlq.DlqMessage;
import com.nori.tc.comm.gateway.domain.dlq.DlqReasonCode;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * mailbox cycle 완료 로그에서 다건 처리 미리보기 개수를 제한하기 위한 상수입니다.
     */
    private static final int LOG_PREVIEW_LIMIT = 5;

    /**
     * 설비 이벤트 생명주기 시작/실패 로그를 남기는 로거입니다.
     *
     * <p>이 로거는 "이벤트 단위" 관측용 로그만 담당하며, 스케줄러/운영 잡로그와는 목적이 다릅니다.</p>
     */
    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;

    private final InboundPipelinePort inboundPipelinePort;
    private final OutboundSenderPort outboundSenderPort;

    private final RouteAndPublishUseCase routeAndPublishUseCase;

    private final DlqPublisherPort dlqPublisherPort;
    private final QuarantinePort quarantinePort;
    /**
     * 이벤트 단위 로그 컨텍스트(MDC 등) 스코프를 열어주는 포트입니다.
     *
     * <p>core는 구체 구현(logback/MDC)을 몰라도 되도록 포트로 추상화합니다.</p>
     */
    private final EventLogContextPort eventLogContextPort;

    /**
     * 한 번 drain 호출 시, 큐에서 최대 몇 개 chunk를 처리할지(공정성/지연 제어)
     */
    private final int maxChunksPerDrain;

    /**
     * 단일 drain 호출의 처리 요약 결과입니다.
     *
     * <p>gateway-core가 mailbox cycle 완료 로그를 기록할 때 traceId/건수/처리시간을 함께 남길 수 있도록
     * core에서 집계한 최소 관측 정보를 전달합니다.</p>
     */
    public record DrainSummary(
            int processedChunks,
            int parsedMessageCount,
            int outboundFrameCount,
            String singleTraceId,
            List<String> traceIdsPreview,
            List<String> messageNamesPreview,
            long startedAtEpochMs,
            long endedAtEpochMs,
            boolean failed,
            String failureReason
    ) {
        /**
         * 불변 미리보기 목록을 보장합니다.
         *
         * @param processedChunks 처리한 inbound chunk 개수
         * @param parsedMessageCount 파싱 완료 메시지 개수
         * @param outboundFrameCount 전송 시도 outbound frame 개수
         * @param singleTraceId 단건 파싱 시 traceId
         * @param traceIdsPreview traceId 미리보기 목록
         * @param messageNamesPreview messageName 미리보기 목록
         * @param startedAtEpochMs drain 시작 시각(epoch ms)
         * @param endedAtEpochMs drain 종료 시각(epoch ms)
         * @param failed 처리 실패 여부
         * @param failureReason 실패 사유 요약
         */
        public DrainSummary {
            if (traceIdsPreview == null) {
                traceIdsPreview = List.of();
            } else {
                traceIdsPreview = List.copyOf(traceIdsPreview);
            }
            if (messageNamesPreview == null) {
                messageNamesPreview = List.of();
            } else {
                messageNamesPreview = List.copyOf(messageNamesPreview);
            }
        }
    }

    
    /**
     * 통신 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @param clockPort 통신 코어 모듈 처리에 사용하는 입력 값
     * @param traceIdGeneratorPort 통신 코어 모듈 처리에 사용하는 입력 값
     * @param inboundPipelinePort 통신 코어 모듈 처리에 사용하는 입력 값
     * @param outboundSenderPort 통신 코어 모듈 처리에 사용하는 입력 값
     * @param routeAndPublishUseCase 통신 코어 모듈 처리에 사용하는 입력 값
     * @param dlqPublisherPort 통신 코어 모듈 처리에 사용하는 입력 값
     * @param quarantinePort 통신 코어 모듈 처리에 사용하는 입력 값
     * @param maxChunksPerDrain 통신 코어 모듈 처리에 사용하는 입력 값
     */
    public EqpSequentialProcessor(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final InboundPipelinePort inboundPipelinePort,
            final OutboundSenderPort outboundSenderPort,
            final RouteAndPublishUseCase routeAndPublishUseCase,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort,
            final EventLogContextPort eventLogContextPort,
            final int maxChunksPerDrain
    ) {
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceIdGeneratorPort = Objects.requireNonNull(traceIdGeneratorPort, "traceIdGeneratorPort is null");
        this.inboundPipelinePort = Objects.requireNonNull(inboundPipelinePort, "inboundPipelinePort is null");
        this.outboundSenderPort = Objects.requireNonNull(outboundSenderPort, "outboundSenderPort is null");
        this.routeAndPublishUseCase = Objects.requireNonNull(routeAndPublishUseCase, "routeAndPublishUseCase is null");
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
        this.quarantinePort = Objects.requireNonNull(quarantinePort, "quarantinePort is null");
        this.eventLogContextPort = eventLogContextPort == null ? EventLogContextPort.noOp() : eventLogContextPort;

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
    public DrainSummary drain(final EquipmentRuntimeContext ctx) {
        Objects.requireNonNull(ctx, "ctx is null");

        final long startedAtEpochMs = clockPort.nowEpochMillis();
        int processedChunks = 0;
        int parsedMessageCount = 0;
        int outboundFrameCount = 0;
        String firstTraceId = null;
        final List<String> traceIdsPreview = new ArrayList<>(LOG_PREVIEW_LIMIT);
        final List<String> messageNamesPreview = new ArrayList<>(LOG_PREVIEW_LIMIT);

        while (processedChunks < maxChunksPerDrain) {
            final InboundChunk chunk = ctx.inboundQueue().poll();
            if (chunk == null) {
                return new DrainSummary(
                        processedChunks,
                        parsedMessageCount,
                        outboundFrameCount,
                        parsedMessageCount == 1 ? firstTraceId : null,
                        traceIdsPreview,
                        messageNamesPreview,
                        startedAtEpochMs,
                        clockPort.nowEpochMillis(),
                        false,
                        null
                ); // 더 이상 처리할 chunk 없음
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
                    outboundFrameCount++;
                }

                // 4) parsed messages 라우팅/발행
                for (ParsedMessage message : result.parsedMessages()) {
                    parsedMessageCount++;
                    if (parsedMessageCount == 1 && message != null) {
                        firstTraceId = message.traceId();
                    } else if (parsedMessageCount > 1) {
                        firstTraceId = null;
                    }
                    addPreview(traceIdsPreview, message == null ? null : message.traceId());
                    addPreview(messageNamesPreview,
                            message == null || message.messageName() == null ? null : message.messageName().value());

                    final AutoCloseable eventLogContext = openEventLogContextSafely(message);
                    try {
                        routeAndPublishUseCase.routeAndPublish(message);
                    } finally {
                        closeEventLogContextSafely(eventLogContext);
                    }
                }

            } catch (Exception ex) {
                // 한 설비 문제를 전체로 번지지 않게: DLQ + Quarantine
                handleFailure(ctx, chunk, ex);
                return new DrainSummary(
                        processedChunks,
                        parsedMessageCount,
                        outboundFrameCount,
                        parsedMessageCount == 1 ? firstTraceId : null,
                        traceIdsPreview,
                        messageNamesPreview,
                        startedAtEpochMs,
                        clockPort.nowEpochMillis(),
                        true,
                        safeMessage(ex)
                );
            }
        }

        return new DrainSummary(
                processedChunks,
                parsedMessageCount,
                outboundFrameCount,
                parsedMessageCount == 1 ? firstTraceId : null,
                traceIdsPreview,
                messageNamesPreview,
                startedAtEpochMs,
                clockPort.nowEpochMillis(),
                false,
                null
        );
    }

    // -------------------------
    // internal
    // -------------------------

    
    /**
     * 통신 코어 모듈 입력 이벤트/요청을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @param ctx 통신 코어 모듈 처리에 사용하는 입력 값
     * @param chunk 통신 코어 모듈 처리에 사용하는 입력 값
     * @param ex 통신 코어 모듈 처리에 사용하는 입력 값
     */
    private void handleFailure(final EquipmentRuntimeContext ctx, final InboundChunk chunk, final Exception ex) {
        // 처리 단계: 분기 조건에 따라 흐름을 제어하고 후속 작업을 호출합니다.
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

    /**
     * 이벤트 단위 로그 컨텍스트를 안전하게 엽니다.
     *
     * <p>로깅 컨텍스트 생성 실패가 실제 이벤트 처리 흐름을 중단시키면 안 되므로,
     * 실패 시 no-op 컨텍스트로 대체하고 디버그 로그만 남깁니다.</p>
     *
     * @param message 현재 처리 중인 파싱 이벤트
     * @return null 이 아닌 closeable 컨텍스트
     */
    private AutoCloseable openEventLogContextSafely(final ParsedMessage message) {
        if (message == null) {
            return EventLogContextPort.NoOpCloseable.INSTANCE;
        }
        try {
            final EventLogContextPort.EventLogContextRequest request = new EventLogContextPort.EventLogContextRequest(
                    message.equipmentId().value(),
                    message.traceId(),
                    message.messageName().value(),
                    message.commInterfaceType().name(),
                    message.socketType()
            );
            final AutoCloseable context = eventLogContextPort.open(request);
            return context == null ? EventLogContextPort.NoOpCloseable.INSTANCE : context;
        } catch (Exception contextOpenFailure) {
            return EventLogContextPort.NoOpCloseable.INSTANCE;
        }
    }

    /**
     * 이벤트 단위 로그 컨텍스트를 안전하게 닫습니다.
     *
     * <p>컨텍스트 close 실패 역시 비즈니스 처리 결과를 바꾸지 않도록 삼키고 디버그 로그만 남깁니다.</p>
     *
     * @param context 닫을 컨텍스트
     * @param message 현재 처리 중인 파싱 이벤트(로그 보강용)
     */
    private void closeEventLogContextSafely(final AutoCloseable context) {
        if (context == null) {
            return;
        }
        try {
            context.close();
        } catch (Exception contextCloseFailure) {
            // 이벤트 처리 본 흐름을 보호하기 위해 로그 컨텍스트 close 실패는 삼킵니다.
            // 관측용 MDC 복구 실패가 비즈니스 처리 결과를 바꾸면 안 되므로 no-op 처리합니다.
        }
    }

    
    /**
     * 통신 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @param ex 통신 코어 모듈 처리에 사용하는 입력 값
     * @return 통신 코어 모듈 처리 결과
     */
    private static String safeMessage(final Exception ex) {
        final String msg = ex.getMessage();
        if (msg == null) return ex.getClass().getSimpleName();

        // 운영 로그 폭주 방지: 메시지를 너무 길게 싣지 않습니다.
        final int limit = 300;
        return msg.length() <= limit ? msg : msg.substring(0, limit) + "...";
    }

    /**
     * 로그 미리보기 목록에 값을 제한 개수 내에서만 추가합니다.
     *
     * <p>mailbox cycle 완료 로그는 단일 라인 가독성이 중요하므로 앞부분만 보존합니다.</p>
     *
     * @param previewList 미리보기 대상 목록
     * @param value 추가할 값
     */
    private static void addPreview(final List<String> previewList, final String value) {
        if (previewList == null) {
            return;
        }
        if (previewList.size() >= LOG_PREVIEW_LIMIT) {
            return;
        }
        if (value == null || value.isBlank()) {
            previewList.add("N/A");
            return;
        }
        previewList.add(value.trim());
    }
}
