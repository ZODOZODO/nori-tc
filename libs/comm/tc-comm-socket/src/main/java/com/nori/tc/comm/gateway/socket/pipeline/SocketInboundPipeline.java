package com.nori.tc.comm.gateway.socket.pipeline;

import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.message.InboundProcessResult;
import com.nori.tc.comm.core.message.MessageName;
import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.socket.config.SocketTypeConfig;
import com.nori.tc.comm.gateway.socket.frame.SocketFrame;
import com.nori.tc.comm.gateway.socket.plugin.spi.GatewaySocketPluginRuntimeProvider;
import com.nori.tc.comm.gateway.socket.runtime.SocketRuntimeContext;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SOCKET inbound 파이프라인 구현체입니다.
 *
 * <p>처리 순서:</p>
 * <p>1) 설비 컨텍스트에서 socketType 및 런타임 버퍼를 조회합니다.</p>
 * <p>2) 설비별 플러그인 핸들러가 있으면 우선 사용하고, 없으면 기본 registry 핸들러를 사용합니다.</p>
 * <p>3) 프레임 추출/디코딩을 반복하여 ParsedMessage 목록으로 정규화합니다.</p>
 * <p>4) 코어 후속 단계(EqpSequentialProcessor)가 처리할 InboundProcessResult를 반환합니다.</p>
 */
public final class SocketInboundPipeline implements InboundPipelinePort {

    /**
     * 파이프라인 동작 추적 로그입니다.
     *
     * <p>핸들러 선택 결과, 파싱 건수 등 운영 디버깅 시 필요한 정보만 debug 로 남깁니다.</p>
     */
    private static final Logger log = LoggerFactory.getLogger(SocketInboundPipeline.class);

    /**
     * 현재 시각(epoch millis) 제공 포트입니다.
     */
    private final ClockPort clockPort;

    /**
     * 메시지 단위 traceId 생성 포트입니다.
     */
    private final TraceIdGeneratorPort traceIdGeneratorPort;

    /**
     * 설비별 SOCKET 플러그인 핸들러 조회 포트입니다.
     */
    private final GatewaySocketPluginRuntimeProvider pluginRuntimeProvider;

    /**
     * 파이프라인 필수 의존성을 주입받습니다.
     *
     * @param clockPort 현재 시각 조회 포트
     * @param traceIdGeneratorPort traceId 생성 포트
     * @param pluginRuntimeProvider 설비별 플러그인 핸들러 조회 포트
     */
    public SocketInboundPipeline(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final GatewaySocketPluginRuntimeProvider pluginRuntimeProvider
    ) {
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceIdGeneratorPort = Objects.requireNonNull(traceIdGeneratorPort, "traceIdGeneratorPort is null");
        this.pluginRuntimeProvider = Objects.requireNonNull(pluginRuntimeProvider, "pluginRuntimeProvider is null");
    }

    /**
     * SOCKET 런타임 컨텍스트를 drain 하여 파싱 결과를 반환합니다.
     *
     * @param ctx 장비 런타임 컨텍스트
     * @return 파싱된 메시지 목록 + 즉시 outbound 프레임 목록(현재는 없음)
     */
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

        final String eqpId = profile.equipmentId().value();
        final String socketType = profile.socketType();

        // 설비별 플러그인 -> 기본 registry 순서로 핸들러를 결정합니다.
        final SocketTypeHandler handler = selectHandler(eqpId, socketType, registry);

        final List<ParsedMessage> parsedMessages = new ArrayList<>();

        while (true) {
            final SocketFrame frame = handler.tryExtractOne(
                    socketCtx.reassemblyBuffer(),
                    socketTypeConfig.maxFrameBytes()
            );
            if (frame == null) {
                break;
            }

            // 빈 프레임 허용 정책을 위반하면 즉시 예외를 발생시켜 상위 정책으로 위임합니다.
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

        if (log.isDebugEnabled() && !parsedMessages.isEmpty()) {
            log.debug("SOCKET inbound parsing completed. eqpId={}, socketType={}, parsedCount={}",
                    eqpId,
                    socketType,
                    parsedMessages.size());
        }

        // 현재 단계에서는 즉시 outbound 프레임을 생성하지 않습니다.
        return new InboundProcessResult(parsedMessages, List.of());
    }

    /**
     * 설비별 실제 핸들러를 선택합니다.
     *
     * <p>플러그인 핸들러가 존재하면 해당 핸들러를 사용하고,
     * 존재하지 않으면 기존 registry 기반 핸들러를 사용합니다.</p>
     *
     * @param eqpId 설비 ID
     * @param socketType 설비 socketType
     * @param registry 기본 핸들러 레지스트리
     * @return 실제 파싱/인코딩에 사용할 핸들러
     */
    private SocketTypeHandler selectHandler(
            final String eqpId,
            final String socketType,
            final SocketTypeRegistry registry
    ) {
        final SocketTypeHandler pluginHandler = pluginRuntimeProvider.findByEqpId(eqpId).orElse(null);
        if (pluginHandler != null) {
            if (log.isDebugEnabled()) {
                log.debug("SOCKET plugin handler selected. eqpId={}, declaredSocketType={}, handlerClass={}",
                        eqpId,
                        pluginHandler.socketType(),
                        pluginHandler.getClass().getName());
            }
            return pluginHandler;
        }
        return registry.getRequired(socketType);
    }
}
