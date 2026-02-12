package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.config.GatewayNettyProperties;
import com.nori.tc.comm.gateway.config.GatewaySocketProperties;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Decoder;
import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * GatewayChannelHandler 생성 팩토리.
 *
 * 역할
 * - PASSIVE/ACTIVE 연결 유형에 맞는 핸들러 인스턴스를 생성합니다.
 * - HSMS/SOCKET eqpId 추출기 및 공통 의존성을 일관되게 주입합니다.
 */
@Component
public class GatewayChannelHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(GatewayChannelHandlerFactory.class);

    private final GatewayNettyProperties nettyProperties;
    private final GatewayProcessingService processingService;
    private final EqpBindingService bindingService;
    private final BindAttemptExecutor bindExecutor;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;

    private final HsmsEqpIdExtractor hsmsExtractor;
    private final SocketEqpIdExtractor socketExtractor;

    
    /**
     * 게이트웨이 Netty 어댑터 구성 요소를 초기화합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param nettyProperties 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param processingService 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param bindingService 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param bindExecutor 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param metrics 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param logSampler 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param frameExtractor 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param secs2Decoder 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param socketProperties 통신 채널/세션 정보
     * @param socketTypeRegistry 통신 채널/세션 정보
     */
    public GatewayChannelHandlerFactory(
            final GatewayNettyProperties nettyProperties,
            final GatewayProcessingService processingService,
            final EqpBindingService bindingService,
            final BindAttemptExecutor bindExecutor,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler,
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder,
            final GatewaySocketProperties socketProperties,
            final SocketTypeRegistry socketTypeRegistry
    ) {
        this.nettyProperties = Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService is null");
        this.bindExecutor = Objects.requireNonNull(bindExecutor, "bindExecutor is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");

        this.hsmsExtractor = new HsmsEqpIdExtractor(frameExtractor, secs2Decoder);
        this.socketExtractor = new SocketEqpIdExtractor(socketProperties, nettyProperties, socketTypeRegistry);
    }

    
    /**
     * 게이트웨이 Netty 어댑터 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param interfaceType 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public GatewayChannelHandler newPassiveHandler(final CommInterfaceType interfaceType) {
        if (log.isDebugEnabled()) {
            log.debug("Create PASSIVE handler. interfaceType={}", interfaceType);
        }
        return new GatewayChannelHandler(
                interfaceType,
                null,
                nettyProperties,
                processingService,
                bindingService,
                metrics,
                logSampler,
                hsmsExtractor,
                socketExtractor,
                bindExecutor
        );
    }

    
    /**
     * 게이트웨이 Netty 어댑터 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param interfaceType 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param eqpId 설비 식별 정보
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public GatewayChannelHandler newActiveHandler(final CommInterfaceType interfaceType, final String eqpId) {
        if (log.isDebugEnabled()) {
            log.debug("Create ACTIVE handler. interfaceType={}, eqpId={}", interfaceType, eqpId);
        }
        return new GatewayChannelHandler(
                interfaceType,
                eqpId,
                nettyProperties,
                processingService,
                bindingService,
                metrics,
                logSampler,
                hsmsExtractor,
                socketExtractor,
                bindExecutor
        );
    }
}
