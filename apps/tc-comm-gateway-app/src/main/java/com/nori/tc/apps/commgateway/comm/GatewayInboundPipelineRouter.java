package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.message.InboundProcessResult;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.socket.pipeline.SocketInboundPipeline;

import java.util.Objects;

/**
 * HSMS/SOCKET 파이프라인 라우터
 *
 * - ctx 타입을 기준으로 파이프라인을 분기합니다.
 * - eqp별 순차 처리 루프(EqpSequentialProcessor)에서 사용됩니다.
 */
public final class GatewayInboundPipelineRouter implements InboundPipelinePort {

    private final HsmsInboundPipeline hsmsInboundPipeline;
    private final SocketInboundPipeline socketInboundPipeline;

    public GatewayInboundPipelineRouter(
            final HsmsInboundPipeline hsmsInboundPipeline,
            final SocketInboundPipeline socketInboundPipeline
    ) {
        this.hsmsInboundPipeline = Objects.requireNonNull(hsmsInboundPipeline, "hsmsInboundPipeline is null");
        this.socketInboundPipeline = Objects.requireNonNull(socketInboundPipeline, "socketInboundPipeline is null");
    }

    @Override
    public InboundProcessResult drain(final EquipmentRuntimeContext ctx) {
        Objects.requireNonNull(ctx, "ctx is null");

        if (ctx instanceof GatewayHsmsRuntimeContext) {
            return hsmsInboundPipeline.drain(ctx);
        }

        if (ctx instanceof GatewaySocketRuntimeContext) {
            return socketInboundPipeline.drain(ctx);
        }

        throw new IllegalArgumentException("Unsupported runtime context: " + ctx.getClass().getSimpleName());
    }
}
