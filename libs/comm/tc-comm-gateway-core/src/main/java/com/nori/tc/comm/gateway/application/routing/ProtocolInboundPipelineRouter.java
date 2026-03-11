package com.nori.tc.comm.gateway.application.routing;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.message.InboundProcessResult;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.gateway.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.gateway.runtime.context.HsmsEquipmentRuntimeContext;
import com.nori.tc.comm.gateway.runtime.context.SocketEquipmentRuntimeContext;
import com.nori.tc.comm.gateway.socket.pipeline.SocketInboundPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 프로토콜별 inbound 파이프라인 라우터입니다.
 *
 * <p>{@link EquipmentRuntimeContext}의 실제 구현 타입을 기준으로 HSMS/SOCKET 파이프라인을 분기합니다.</p>
 * <p>설비별 순차 처리 루프에서 호출되며, 프로토콜 판별만 담당하고 실제 파싱/검증/도메인 변환은
 * 각 파이프라인 구현체에 위임합니다.</p>
 */
public final class ProtocolInboundPipelineRouter implements InboundPipelinePort {

    private static final Logger log = LoggerFactory.getLogger(ProtocolInboundPipelineRouter.class);

    private final HsmsInboundPipeline hsmsInboundPipeline;
    private final SocketInboundPipeline socketInboundPipeline;

    /**
     * 프로토콜 라우터 의존성을 초기화합니다.
     *
     * @param hsmsInboundPipeline HSMS 전용 inbound 파이프라인
     * @param socketInboundPipeline SOCKET 전용 inbound 파이프라인
     */
    public ProtocolInboundPipelineRouter(
            final HsmsInboundPipeline hsmsInboundPipeline,
            final SocketInboundPipeline socketInboundPipeline
    ) {
        this.hsmsInboundPipeline = Objects.requireNonNull(hsmsInboundPipeline, "hsmsInboundPipeline is null");
        this.socketInboundPipeline = Objects.requireNonNull(socketInboundPipeline, "socketInboundPipeline is null");

        log.info("ProtocolInboundPipelineRouter initialized. supportedProtocols=[SECS, SOCKET]");
    }

    /**
     * 런타임 컨텍스트 타입을 기준으로 적절한 inbound 파이프라인으로 위임합니다.
     *
     * @param ctx 설비별 런타임 컨텍스트(프로토콜별 구현체)
     * @return 프로토콜 파이프라인 처리 결과
     */
    @Override
    public InboundProcessResult drain(final EquipmentRuntimeContext ctx) {
        Objects.requireNonNull(ctx, "ctx is null");

        if (ctx instanceof HsmsEquipmentRuntimeContext) {
            if (log.isTraceEnabled()) {
                log.trace("Inbound pipeline routed to HSMS. eqpId={}", ctx.profile().equipmentId().value());
            }
            return hsmsInboundPipeline.drain(ctx);
        }

        if (ctx instanceof SocketEquipmentRuntimeContext) {
            if (log.isTraceEnabled()) {
                log.trace("Inbound pipeline routed to SOCKET. eqpId={}", ctx.profile().equipmentId().value());
            }
            return socketInboundPipeline.drain(ctx);
        }

        log.warn("Inbound pipeline routing failed. unsupportedContextType={}", ctx.getClass().getName());
        throw new IllegalArgumentException("Unsupported runtime context: " + ctx.getClass().getSimpleName());
    }
}
