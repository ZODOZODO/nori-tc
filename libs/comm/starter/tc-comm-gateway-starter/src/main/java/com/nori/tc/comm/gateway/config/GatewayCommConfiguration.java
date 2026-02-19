package com.nori.tc.comm.gateway.config;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.plugin.socket.GatewaySocketPluginRuntimeProperties;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.core.port.impl.SystemClock;
import com.nori.tc.comm.core.port.impl.UlidTraceIdGenerator;
import com.nori.tc.comm.core.routing.PublishPolicy;
import com.nori.tc.comm.core.routing.PublishPolicyEngine;
import com.nori.tc.comm.gateway.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.gateway.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.gateway.hsms.secs.BasicSecs2Decoder;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Decoder;
import com.nori.tc.comm.gateway.socket.pipeline.SocketInboundPipeline;
import com.nori.tc.comm.gateway.socket.plugin.GatewaySocketPluginRuntimeProvider;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;
import com.nori.tc.comm.gateway.socket.socketType.types.lineDelimited.LineDelimitedSocketTypeHandler;
import com.nori.tc.comm.gateway.socket.socketType.types.regexDelimited.RegexDelimitedSocketTypeHandler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
        GatewayLifecycleProperties.class,
        GatewayHsmsProperties.class,
        GatewaySocketProperties.class,
        GatewayKafkaTopicProperties.class,
        GatewayKafkaClientProperties.class,
        GatewayKafkaShardProperties.class,
        GatewayUiTaskPolicyProperties.class,
        GatewayRedisProperties.class,
        GatewayPublishPolicyProperties.class,
        GatewayNettyProperties.class,
        GatewayObservabilityProperties.class,
        GatewaySocketPluginRuntimeProperties.class
})
/**
 * GatewayCommConfiguration 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */

public class GatewayCommConfiguration {

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public ClockPort clockPort() {
        return new SystemClock();
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public TraceIdGeneratorPort traceIdGeneratorPort() {
        return new UlidTraceIdGenerator();
    }

    /**
     * SOCKET 플러그인 런타임이 없는 환경을 위한 기본 no-op provider 입니다.
     *
     * <p>plugin-adapter 모듈이 포함되면 해당 모듈의 실제 구현체가 주입되고,
     * 포함되지 않으면 이 기본 구현체가 fallback 으로 사용됩니다.</p>
     *
     * @return 항상 empty 를 반환하는 no-op provider
     */
    @Bean
    @ConditionalOnMissingBean(GatewaySocketPluginRuntimeProvider.class)
    public GatewaySocketPluginRuntimeProvider gatewaySocketPluginRuntimeProvider() {
        return GatewaySocketPluginRuntimeProvider.noop();
    }

    
    /**
     * 게이트웨이 스타터 구성 메시지 또는 이벤트를 발행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param properties 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public PublishPolicy publishPolicy(final GatewayPublishPolicyProperties properties) {
        return new PublishPolicyEngine(properties.toSpec());
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param hsmsProperties 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public HsmsFrameExtractor hsmsFrameExtractor(final GatewayHsmsProperties hsmsProperties) {
        // Enforce a max frame size from runtime properties to protect memory/CPU.
        return new HsmsFrameExtractor(hsmsProperties.getMaxFrameBytes());
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public Secs2Decoder secs2Decoder() {
        return new BasicSecs2Decoder();
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param clockPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param traceIdGeneratorPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param frameExtractor 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param secs2Decoder 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public HsmsInboundPipeline hsmsInboundPipeline(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder
    ) {
        return new HsmsInboundPipeline(clockPort, traceIdGeneratorPort, frameExtractor, secs2Decoder);
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param clockPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param traceIdGeneratorPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public SocketInboundPipeline socketInboundPipeline(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final GatewaySocketPluginRuntimeProvider pluginRuntimeProvider
    ) {
        return new SocketInboundPipeline(clockPort, traceIdGeneratorPort, pluginRuntimeProvider);
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param socketProperties 통신 채널/세션 정보
     * @return 게이트웨이 스타터 구성 처리 결과
     */
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
