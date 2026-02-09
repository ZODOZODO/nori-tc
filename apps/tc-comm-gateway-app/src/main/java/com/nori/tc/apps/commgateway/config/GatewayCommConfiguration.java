package com.nori.tc.apps.commgateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * tc-comm-gateway 핵심 구성 빈
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
    public HsmsFrameExtractor hsmsFrameExtractor() {
        return new HsmsFrameExtractor();
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

        // 기본 제공 핸들러 등록
        registry.register(new LineDelimitedSocketTypeHandler());
        registry.register(new RegexDelimitedSocketTypeHandler(socketProperties.getRegexEndPattern()));

        return registry;
    }

    @Bean
    public ObjectMapper objectMapper() {
        // Spring Boot 기본 ObjectMapper를 사용해도 되지만,
        // 명시적으로 Bean을 노출해 모듈 간 주입 경로를 명확히 합니다.
        return new ObjectMapper();
    }
}
