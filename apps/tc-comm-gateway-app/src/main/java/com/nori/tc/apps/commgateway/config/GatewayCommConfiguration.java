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
        // HSMS 프레임 최대 크기는 runtime property에서 관리한다.
        // - oversized frame 방지로 메모리/CPU 과부하를 차단
        // - tc.comm.gateway.hsms.max-frame-bytes 로 운영 중 조절 가능
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

    // ObjectMapper Bean은 의도적으로 제공하지 않는다.
    // - 현재 앱에서 직접 사용하지 않음
    // - jackson 의존성을 불필요하게 강제하지 않기 위해 필요 시에만 추가
}
