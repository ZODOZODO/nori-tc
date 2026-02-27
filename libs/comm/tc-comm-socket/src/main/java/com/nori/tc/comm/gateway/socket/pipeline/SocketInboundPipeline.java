package com.nori.tc.comm.gateway.socket.pipeline;

import com.nori.tc.comm.core.eqp.EquipmentProfile;
import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.message.InboundProcessResult;
import com.nori.tc.comm.core.message.MessageName;
import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.InboundPipelinePort;
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
import org.slf4j.MDC;

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
     * 단건 요약 로그 출력 시 MDC traceId 주입에 사용하는 키입니다.
     */
    private static final String TRACE_ID_MDC_KEY = "traceId";

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
            final GatewaySocketPluginRuntimeProvider pluginRuntimeProvider
    ) {
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
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
            final String traceId = resolveRequiredTraceId(socketCtx, eqpId, socketType);

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
            if (parsedMessages.size() == 1) {
                final ParsedMessage first = parsedMessages.get(0);
                logSingleParseSummaryWithTraceContext(eqpId, socketType, parsedMessages.size(), first);
            } else {
                log.debug("GW_EQP_RX_PARSE_SUMMARY. eqpId={}, interfaceType={}, socketType={}, parsedCount={}, traceIds={}, messageNames={}",
                        eqpId,
                        CommInterfaceType.SOCKET.name(),
                        socketType,
                        parsedMessages.size(),
                        previewTraceIds(parsedMessages),
                        previewMessageNames(parsedMessages));
            }
        }

        // 현재 단계에서는 즉시 outbound 프레임을 생성하지 않습니다.
        return new InboundProcessResult(parsedMessages, List.of());
    }

    /**
     * 단건 파싱 요약 로그를 출력할 때 MDC traceId 헤더를 임시로 맞춰 기록합니다.
     *
     * <p>출력 이후에는 기존 MDC traceId 값을 반드시 복구해 후속 로그 오염을 방지합니다.</p>
     *
     * @param eqpId       장비 ID
     * @param socketType  소켓 타입
     * @param parsedCount 파싱 건수(단건 시 1)
     * @param message     단건 파싱 메시지
     */
    private void logSingleParseSummaryWithTraceContext(
            final String eqpId,
            final String socketType,
            final int parsedCount,
            final ParsedMessage message
    ) {
        final String previousTraceId = MDC.get(TRACE_ID_MDC_KEY);
        final String traceId = message.traceId();
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
        }
        try {
            log.debug("GW_EQP_RX_PARSE_SUMMARY. eqpId={}, interfaceType={}, socketType={}, parsedCount={}, traceId={}, messageName={}",
                    eqpId,
                    CommInterfaceType.SOCKET.name(),
                    socketType,
                    parsedCount,
                    message.traceId(),
                    message.messageName().value());
        } finally {
            restoreTraceIdMdc(previousTraceId);
        }
    }

    /**
     * 저장해둔 기존 traceId 값을 MDC로 복구합니다.
     *
     * @param previousTraceId 단건 로그 출력 이전 traceId 값
     */
    private static void restoreTraceIdMdc(final String previousTraceId) {
        if (previousTraceId == null || previousTraceId.isBlank()) {
            MDC.remove(TRACE_ID_MDC_KEY);
            return;
        }
        MDC.put(TRACE_ID_MDC_KEY, previousTraceId);
    }

    /**
     * Extracts the required traceId from the reassembly provenance state.
     *
     * <p>SOCKET inbound parser must never create a second traceId. The parser
     * resolves the traceId that was already assigned when the source bytes were
     * enqueued and consumed by frame extraction.</p>
     *
     * @param socketCtx socket runtime context that owns the reassembly buffer
     * @param eqpId equipment id used for diagnostic messages
     * @param socketType socket type used for diagnostic messages
     * @return traceId that belongs to the consumed frame bytes
     */
    private static String resolveRequiredTraceId(
            final SocketRuntimeContext socketCtx,
            final String eqpId,
            final String socketType
    ) {
        final String traceId = socketCtx.reassemblyBuffer().lastDiscardedTraceId();
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalStateException(
                    "Trace provenance is missing after SOCKET frame extraction. eqpId="
                            + eqpId
                            + ", socketType="
                            + socketType
            );
        }
        return traceId;
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

    /**
     * 파싱된 메시지 목록에서 traceId 미리보기를 한 줄 문자열로 구성합니다.
     *
     * @param parsedMessages 파싱 완료 메시지 목록
     * @return traceId 미리보기 문자열
     */
    private static String previewTraceIds(final List<ParsedMessage> parsedMessages) {
        return previewParsedField(parsedMessages, true);
    }

    /**
     * 파싱된 메시지 목록에서 messageName 미리보기를 한 줄 문자열로 구성합니다.
     *
     * @param parsedMessages 파싱 완료 메시지 목록
     * @return messageName 미리보기 문자열
     */
    private static String previewMessageNames(final List<ParsedMessage> parsedMessages) {
        return previewParsedField(parsedMessages, false);
    }

    /**
     * 파싱 메시지 목록의 공통 필드(traceId 또는 messageName) 미리보기를 구성합니다.
     *
     * @param parsedMessages 파싱 완료 메시지 목록
     * @param traceField true면 traceId, false면 messageName
     * @return 로그 출력용 미리보기 문자열
     */
    private static String previewParsedField(final List<ParsedMessage> parsedMessages, final boolean traceField) {
        if (parsedMessages == null || parsedMessages.isEmpty()) {
            return "[]";
        }

        final int previewLimit = 5;
        final StringBuilder sb = new StringBuilder(128);
        sb.append('[');
        final int count = Math.min(parsedMessages.size(), previewLimit);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final ParsedMessage message = parsedMessages.get(i);
            if (message == null) {
                sb.append("null");
                continue;
            }
            sb.append(traceField ? message.traceId() : message.messageName().value());
        }
        if (parsedMessages.size() > previewLimit) {
            sb.append(", ...");
        }
        sb.append(']');
        return sb.toString();
    }
}
