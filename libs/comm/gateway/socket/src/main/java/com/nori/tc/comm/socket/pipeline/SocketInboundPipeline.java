package com.nori.tc.comm.socket.pipeline;

import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.message.InboundProcessResult;
import com.nori.tc.comm.core.message.MessageName;
import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.domain.type.CommInterfaceType;
import com.nori.tc.comm.socket.config.SocketTypeConfig;
import com.nori.tc.comm.socket.frame.SocketFrame;
import com.nori.tc.comm.socket.socketType.SocketTypeDecodeResult;
import com.nori.tc.comm.socket.socketType.SocketTypeHandler;
import com.nori.tc.comm.socket.socketType.SocketTypeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SOCKET inbound 파이프라인 구현체
 *
 * 처리 개요
 * 1) ctx.profile.socketType 으로 SocketTypeHandler 선택
 * 2) handler.tryExtractOne(buffer)로 프레임을 가능한 만큼 추출
 * 3) handler.decode(frameBytes)로 messageName + body 생성
 * 4) ParsedMessage로 정규화하여 반환
 *
 * 주의
 * - SOCKET은 응답을 즉시 보내야 하는 경우도 있을 수 있으나,
 *   본 뼈대에서는 “inbound→kafka/outbox” 파이프라인 중심으로 제공합니다.
 * - outbound encode/send는 추후 확장 포인트로 남깁니다.
 */
public final class SocketInboundPipeline implements InboundPipelinePort {

    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;

    public SocketInboundPipeline(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort
    ) {
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceIdGeneratorPort = Objects.requireNonNull(traceIdGeneratorPort, "traceIdGeneratorPort is null");
    }

    @Override
    public InboundProcessResult drain(final EquipmentRuntimeContext ctx) {
        Objects.requireNonNull(ctx, "ctx is null");

        if (!(ctx instanceof SocketRuntimeContext socketCtx)) {
            throw new IllegalArgumentException("ctx must implement SocketRuntimeContext for SOCKET pipeline");
        }

        final long nowMs = clockPort.nowEpochMillis();

        final EquipmentProfile profile = socketCtx.profile();
        final SocketTypeConfig socketTypeConfig = socketCtx.socketTypeConfig();
        final SocketTypeRegistry registry = socketCtx.socketTypeRegistry();

        // 설비의 socketType으로 핸들러 선택
        final String socketType = profile.socketType();
        final SocketTypeHandler handler = registry.getRequired(socketType);

        final List<ParsedMessage> parsedMessages = new ArrayList<>();

        while (true) {
            final SocketFrame frame = handler.tryExtractOne(
                    socketCtx.reassemblyBuffer(),
                    socketTypeConfig.maxFrameBytes()
            );
            if (frame == null) break;

            // 빈 프레임 허용 여부
            if (frame.bytes().length == 0 && !socketTypeConfig.allowEmptyFrame()) {
                throw new IllegalArgumentException("Empty frame is not allowed for socketType=" + socketType);
            }

            final SocketTypeDecodeResult decoded = handler.decode(frame.bytes());

            final String traceId = traceIdGeneratorPort.newTraceId();

            final Map<String, String> attributes = new HashMap<>();
            attributes.put("socketType", socketType);
            attributes.putAll(decoded.attributes());

            parsedMessages.add(new ParsedMessage(
                    profile.equipmentId(),
                    traceId,
                    CommInterfaceType.SOCKET,
                    socketType,
                    new MessageName(decoded.messageName()),
                    nowMs,
                    attributes,
                    decoded.body()
            ));
        }

        // SOCKET은 기본적으로 outboundFrames 없음(필요 시 확장)
        return new InboundProcessResult(parsedMessages, List.of());
    }
}
