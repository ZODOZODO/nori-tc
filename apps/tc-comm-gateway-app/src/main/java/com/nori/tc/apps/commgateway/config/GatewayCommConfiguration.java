package com.nori.tc.apps.commgateway.config;

import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.TraceNoGeneratorPort;
import com.nori.tc.comm.core.port.impl.SystemClock;
import com.nori.tc.comm.core.port.impl.UlidTraceNoGenerator;
import com.nori.tc.comm.core.routing.PublishPolicy;
import com.nori.tc.comm.core.routing.PublishPolicyEngine;
import com.nori.tc.comm.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.hsms.secs.BasicSecs2Decoder;
import com.nori.tc.comm.hsms.secs.Secs2Decoder;
import com.nori.tc.comm.socket.pipeline.SocketInboundPipeline;
import com.nori.tc.comm.socket.socketType.SocketTypeRegistry;
import com.nori.tc.comm.socket.socketType.builtin.lineDelimited.LineDelimitedSocketTypeHandler;
import com.nori.tc.comm.socket.socketType.builtin.regexDelimited.RegexDelimitedSocketTypeHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * tc-comm-gateway wiring configuration.
 *
 * - Registers only in-app beans.
 * - Infrastructure is provided by starters, while app-specific adapters
 *   live in this application module.
 */
@Configuration
@EnableConfigurationProperties({
        GatewayRuntimeProperties.class,
        GatewayHsmsProperties.class,
        GatewaySocketProperties.class,
        GatewayKafkaTopicProperties.class,
        GatewayRedisProperties.class,
        GatewayPublishPolicyProperties.class
})
public class GatewayCommConfiguration {

    @Bean
    public ClockPort clockPort() {
        return new SystemClock();
    }

    @Bean
    public TraceNoGeneratorPort traceNoGeneratorPort() {
        return new UlidTraceNoGenerator();
    }

    @Bean
    public PublishPolicy publishPolicy(final GatewayPublishPolicyProperties properties) {
        return new PublishPolicyEngine(properties.toSpec());
    }

    @Bean
    public HsmsFrameExtractor hsmsFrameExtractor(final GatewayHsmsProperties hsmsProperties) {
        // Enforce a max frame size from runtime properties to protect memory/CPU.
        return new HsmsFrameExtractor(hsmsProperties.getMaxFrameBytes());
    }

    @Bean
    public Secs2Decoder secs2Decoder() {
        return new BasicSecs2Decoder();
    }

    @Bean
    public HsmsInboundPipeline hsmsInboundPipeline(
            final ClockPort clockPort,
            final TraceNoGeneratorPort traceNoGeneratorPort,
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder
    ) {
        return new HsmsInboundPipeline(clockPort, traceNoGeneratorPort, frameExtractor, secs2Decoder);
    }

    @Bean
    public SocketInboundPipeline socketInboundPipeline(
            final ClockPort clockPort,
            final TraceNoGeneratorPort traceNoGeneratorPort
    ) {
        return new SocketInboundPipeline(clockPort, traceNoGeneratorPort);
    }

    @Bean
    public SocketTypeRegistry socketTypeRegistry(final GatewaySocketProperties socketProperties) {
        final SocketTypeRegistry registry = new SocketTypeRegistry();

        // Built-in socket handlers
        registry.register(new LineDelimitedSocketTypeHandler());
        registry.register(new RegexDelimitedSocketTypeHandler(socketProperties.getRegexEndPattern()));

        return registry;
    }

    // Intentionally no ObjectMapper bean here to avoid forcing Jackson into the app.
}
