package com.nori.tc.comm.gateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.message.InboundProcessResult;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.gateway.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.gateway.socket.pipeline.SocketInboundPipeline;

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

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param hsmsInboundPipeline 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param socketInboundPipeline 통신 채널/세션 정보
     */
    public GatewayInboundPipelineRouter(
            final HsmsInboundPipeline hsmsInboundPipeline,
            final SocketInboundPipeline socketInboundPipeline
    ) {
        this.hsmsInboundPipeline = Objects.requireNonNull(hsmsInboundPipeline, "hsmsInboundPipeline is null");
        this.socketInboundPipeline = Objects.requireNonNull(socketInboundPipeline, "socketInboundPipeline is null");
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param ctx 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @return 게이트웨이 코어 모듈 처리 결과
     */
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
