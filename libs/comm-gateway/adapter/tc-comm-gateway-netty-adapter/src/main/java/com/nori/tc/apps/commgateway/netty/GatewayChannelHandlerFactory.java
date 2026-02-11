package com.nori.tc.apps.commgateway.netty;

import com.nori.tc.apps.commgateway.comm.GatewayProcessingService;
import com.nori.tc.apps.commgateway.config.GatewayNettyProperties;
import com.nori.tc.apps.commgateway.config.GatewaySocketProperties;
import com.nori.tc.apps.commgateway.metrics.GatewayLogSampler;
import com.nori.tc.apps.commgateway.metrics.GatewayMetrics;
import com.nori.tc.comm.domain.type.CommInterfaceType;
import com.nori.tc.comm.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.hsms.secs.Secs2Decoder;
import com.nori.tc.comm.socket.socketType.SocketTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * GatewayChannelHandler 생성 팩토리.
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
