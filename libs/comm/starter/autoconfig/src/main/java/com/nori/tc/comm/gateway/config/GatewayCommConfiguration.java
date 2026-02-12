package com.nori.tc.apps.commgateway.config;

import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.core.port.impl.SystemClock;
import com.nori.tc.comm.core.port.impl.UlidTraceIdGenerator;
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

/**
 * tc-comm-gateway 공통 Bean 구성.
 *
 * - 스타터의 AutoConfiguration이 @Import로 로딩한다
 * - 컴포넌트 스캔 대상이 아니므로 중복 등록을 방지한다
 */
@EnableConfigurationProperties({
        GatewayRuntimeProperties.class,
        GatewayHsmsProperties.class,
        GatewaySocketProperties.class,
        GatewayKafkaTopicProperties.class,
        GatewayKafkaClientProperties.class,
        GatewayKafkaShardProperties.class,
        GatewayRedisProperties.class,
        GatewayPublishPolicyProperties.class,
        GatewayNettyProperties.class,
        GatewayObservabilityProperties.class
})
public class GatewayCommConfiguration {

    @Bean
    public ClockPort clockPort() {
        return new SystemClock();
    }

    @Bean
    public TraceIdGeneratorPort traceIdGeneratorPort() {
        return new UlidTraceIdGenerator();
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
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder
    ) {
        return new HsmsInboundPipeline(clockPort, traceIdGeneratorPort, frameExtractor, secs2Decoder);
    }

    @Bean
    public SocketInboundPipeline socketInboundPipeline(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort
    ) {
        return new SocketInboundPipeline(clockPort, traceIdGeneratorPort);
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
