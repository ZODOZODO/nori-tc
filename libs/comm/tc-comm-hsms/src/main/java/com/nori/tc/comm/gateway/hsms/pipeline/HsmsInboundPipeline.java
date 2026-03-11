package com.nori.tc.comm.gateway.hsms.pipeline;

import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.message.InboundProcessResult;
import com.nori.tc.comm.core.message.MessageName;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.hsms.frame.HsmsFrame;
import com.nori.tc.comm.gateway.hsms.frame.HsmsFrameEncoder;
import com.nori.tc.comm.gateway.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.gateway.hsms.runtime.HsmsRuntimeContext;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Decoder;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Message;
import com.nori.tc.comm.gateway.hsms.session.SessionHandleResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HSMS inbound 파이프라인 구현체
 *
 * core의 InboundPipelinePort를 구현하여, 통합 tc-comm-gateway의 eqp 순차 처리 루프에서 사용됩니다.
 *
 * 처리 개요
 * 1) ReassemblyBuffer에서 HSMS 프레임을 가능한 만큼 추출
 * 2) 세션 상태 머신으로 CONTROL 처리(SELECT/LINKTEST/DESELECT/SEPARATE 등)
 * 3) DATA 메시지는 SECS-II 디코더를 통해 최소 모델로 변환
 * 4) ParsedMessage와 OutboundRawFrame 목록을 반환
 *
 * 주의(아키텍처)
 * - Netty 쓰레드에서 이 로직을 호출하면 안 됩니다.
 * - 반드시 eqp별 순차 처리 루프(코어 유스케이스)에서 호출되어야 합니다.
 */
public final class HsmsInboundPipeline implements InboundPipelinePort {

    private final ClockPort clockPort;
    private final HsmsFrameExtractor frameExtractor;
    private final Secs2Decoder secs2Decoder;

    /**
     * systemBytes 생성은 세션/요청 상관관계에 중요합니다.
     * - 본 뼈대에서는 단순 증가 시퀀스를 사용합니다.
     * - 멀티스레드 환경에서 공유하면 안 되므로, 파이프라인 인스턴스를 eqp별로 만들거나,
     *   systemBytesGenerator를 ctx로 옮기는 확장도 고려할 수 있습니다.
     */
    private int systemBytesSequence = 1;

    
    /**
     * HSMS 통신 모듈 구성 요소를 초기화합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param clockPort HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param traceIdGeneratorPort HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param frameExtractor HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param secs2Decoder HSMS 통신 모듈 처리에 사용하는 입력 값
     */
    public HsmsInboundPipeline(
            final ClockPort clockPort,
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder
    ) {
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.frameExtractor = Objects.requireNonNull(frameExtractor, "frameExtractor is null");
        this.secs2Decoder = Objects.requireNonNull(secs2Decoder, "secs2Decoder is null");
    }

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param ctx HSMS 통신 모듈 처리에 사용하는 입력 값
     * @return HSMS 통신 모듈 처리 결과
     */
    @Override
    public InboundProcessResult drain(final EquipmentRuntimeContext ctx) {
        Objects.requireNonNull(ctx, "ctx is null");

        // HSMS용 ctx인지 확인(잘못된 wiring 방지)
        if (!(ctx instanceof HsmsRuntimeContext hsmsCtx)) {
            throw new IllegalArgumentException("ctx must implement HsmsRuntimeContext for HSMS pipeline");
        }

        final long nowMs = clockPort.nowEpochMillis();

        final List<ParsedMessage> parsedMessages = new ArrayList<>();
        final List<OutboundRawFrame> outboundFrames = new ArrayList<>();

        final EquipmentProfile profile = hsmsCtx.profile();

        // 0) 주기 tick 처리(Linktest 등) - “선택적으로” 사용
        // - 상위 레이어가 tick 스케줄링을 갖고 있다면 그쪽에서 호출해도 됩니다.
        final List<HsmsFrame> tickFrames = hsmsCtx.hsmsSession().tick(nowMs, this::nextSystemBytes);
        for (HsmsFrame f : tickFrames) {
            outboundFrames.add(toOutboundRawFrame(profile, f, nowMs, "HSMS_TICK"));
        }

        // 1) 프레임을 가능한 만큼 추출
        while (true) {
            final HsmsFrame frame = frameExtractor.tryExtractOne(hsmsCtx.reassemblyBuffer());
            if (frame == null) break;

            // 2) 세션 상태 머신 처리(SELECT/LINKTEST 등)
            final SessionHandleResult sessionResult = hsmsCtx.hsmsSession().onInboundFrame(frame, nowMs);

            // 2-1) 세션 머신이 만든 control outbound
            for (HsmsFrame control : sessionResult.outboundControlFrames()) {
                outboundFrames.add(toOutboundRawFrame(profile, control, nowMs, "HSMS_CONTROL"));
            }

            // 3) DATA 프레임이면 SECS-II 디코딩 및 ParsedMessage 생성
            if (frame.isDataMessage() && sessionResult.allowDataProcessing()) {
                final Secs2Message secs = secs2Decoder.decode(frame);

                // traceId: 메시지 단위로 신규 생성(운영 추적)
                final String traceId = resolveRequiredTraceId(hsmsCtx, profile.equipmentId().value());

                // attributes: 운영/분석에 유용한 헤더 메타를 담습니다.
                final Map<String, String> attributes = new HashMap<>();
                attributes.put("deviceId", String.valueOf(frame.header().deviceId()));
                attributes.put("systemBytes", String.valueOf(frame.header().systemBytes()));
                attributes.put("wBit", String.valueOf(frame.header().wBit()));
                attributes.put("pType", String.valueOf(frame.header().pType()));
                attributes.put("sType", frame.header().sType().name());

                // tags(ctx.tags)는 core 표준이므로 그대로 유지하는 것이 좋습니다.
                // 본문(body)은 우선 Secs2Message(최소 모델)를 넣습니다.
                parsedMessages.add(new ParsedMessage(
                        profile.equipmentId(),
                        traceId,
                        CommInterfaceType.SECS,
                        null,
                        new MessageName(secs.messageName()),
                        nowMs,
                        attributes,
                        secs
                ));
            }
        }

        return new InboundProcessResult(parsedMessages, outboundFrames);
    }

    /**
     * Resolves mandatory traceId from reassembly provenance.
     *
     * <p>HSMS parser must reuse enqueue-time traceId. A missing provenance
     * indicates an invalid processing state and is treated as a hard failure.</p>
     *
     * @param hsmsCtx runtime context that owns the reassembly buffer
     * @param eqpId equipment id for diagnostics
     * @return traceId for the extracted frame
     */
    private static String resolveRequiredTraceId(
            final HsmsRuntimeContext hsmsCtx,
            final String eqpId
    ) {
        final String traceId = hsmsCtx.reassemblyBuffer().lastDiscardedTraceId();
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalStateException(
                    "Trace provenance is missing after HSMS frame extraction. eqpId=" + eqpId
            );
        }
        return traceId;
    }

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @return HSMS 통신 모듈 처리 결과
     */
    private int nextSystemBytes() {
        // 단순 증가(overflow는 자연스럽게 회전)
        return systemBytesSequence++;
    }

    
    /**
     * HSMS 통신 모듈 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param profile HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param frame HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param nowMs HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param description HSMS 통신 모듈 처리에 사용하는 입력 값
     * @return HSMS 통신 모듈 처리 결과
     */
    private static OutboundRawFrame toOutboundRawFrame(
            final EquipmentProfile profile,
            final HsmsFrame frame,
            final long nowMs,
            final String description
    ) {
        final byte[] bytes = HsmsFrameEncoder.encode(frame);

        return new OutboundRawFrame(
                profile.equipmentId(),
                CommInterfaceType.SECS,
                null,
                bytes,
                nowMs,
                description
        );
    }
}
