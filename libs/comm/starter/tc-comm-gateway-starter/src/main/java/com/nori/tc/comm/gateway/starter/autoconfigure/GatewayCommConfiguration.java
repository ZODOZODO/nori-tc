package com.nori.tc.comm.gateway.starter.autoconfigure;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaClientProperties;
import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.plugin.socket.GatewaySocketPluginRuntimeProperties;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.core.port.impl.SystemClock;
import com.nori.tc.comm.core.port.impl.UlidTraceIdGenerator;
import com.nori.tc.comm.core.routing.PublishPolicy;
import com.nori.tc.comm.core.routing.PublishPolicyEngine;
import com.nori.tc.comm.gateway.config.props.GatewayHsmsProperties;
import com.nori.tc.comm.gateway.config.props.GatewayKafkaShardProperties;
import com.nori.tc.comm.gateway.config.props.GatewayLifecycleProperties;
import com.nori.tc.comm.gateway.config.props.GatewayNettyProperties;
import com.nori.tc.comm.gateway.config.props.GatewayObservabilityProperties;
import com.nori.tc.comm.gateway.config.props.GatewayPublishPolicyProperties;
import com.nori.tc.comm.gateway.config.props.GatewayRedisProperties;
import com.nori.tc.comm.gateway.config.props.GatewayRuntimeProperties;
import com.nori.tc.comm.gateway.config.props.GatewaySocketProperties;
import com.nori.tc.comm.gateway.config.props.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.hsms.frame.HsmsFrameExtractor;
import com.nori.tc.comm.gateway.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.gateway.hsms.secs.BasicSecs2Decoder;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Decoder;
import com.nori.tc.comm.gateway.socket.pipeline.SocketInboundPipeline;
import com.nori.tc.comm.gateway.socket.plugin.spi.GatewaySocketPluginRuntimeProvider;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;
import com.nori.tc.comm.gateway.socket.socketType.types.lineDelimited.LineDelimitedSocketTypeHandler;
import com.nori.tc.comm.gateway.socket.socketType.types.regexDelimited.RegexDelimitedSocketTypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 게이트웨이 공통 런타임 Bean을 등록하는 자동구성 클래스입니다.
 *
 * <p>이 클래스의 책임은 다음과 같습니다.</p>
 * <p>1) 게이트웨이 코어 처리에 필요한 공통 Bean(시계, TraceId, 파이프라인, 레지스트리)을 생성합니다.</p>
 * <p>2) 게이트웨이 설정 속성 클래스를 {@link EnableConfigurationProperties}로 활성화합니다.</p>
 * <p>3) 플러그인 어댑터가 없는 환경에서도 동작 가능한 기본(no-op) Bean을 제공합니다.</p>
 *
 * <p>주의:</p>
 * <p>- 이 클래스는 스타터 자동구성에서 {@code @Import}로 로딩되므로 컴포넌트 스캔 대상이 아닙니다.</p>
 * <p>- Bean 생성 로그는 시작 시점 진단을 위해 {@code debug} 중심으로 기록하고,
 *   fallback/no-op 같은 운영 판단 포인트는 {@code info}로 기록합니다.</p>
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
public class GatewayCommConfiguration {

    /**
     * 자동구성 단계 Bean 생성 흐름 추적용 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewayCommConfiguration.class);

    /**
     * 시스템 시계 포트를 생성합니다.
     *
     * <p>운영 코드에서는 현재 시각 조회를 직접 호출하지 않고 이 포트를 통해 접근하여,
     * 테스트 대역(Mock/Fake)으로 교체 가능한 구조를 유지합니다.</p>
     *
     * @return 시스템 시계 기반 {@link ClockPort} 구현체
     */
    @Bean
    public ClockPort clockPort() {
        if (log.isTraceEnabled()) {
            log.trace("공통 Bean 생성: ClockPort(SystemClock)");
        }
        return new SystemClock();
    }

    /**
     * TraceId 생성 포트를 생성합니다.
     *
     * <p>게이트웨이 전체 로그/이벤트/DLQ 추적 일관성을 위해 ULID 기반 TraceId 생성기를 기본값으로 사용합니다.</p>
     *
     * @return ULID 기반 TraceId 생성 포트
     */
    @Bean
    public TraceIdGeneratorPort traceIdGeneratorPort() {
        if (log.isTraceEnabled()) {
            log.trace("공통 Bean 생성: TraceIdGeneratorPort(UlidTraceIdGenerator)");
        }
        return new UlidTraceIdGenerator();
    }

    /**
     * SOCKET 플러그인 런타임 제공자가 없을 때 사용할 기본 no-op provider를 생성합니다.
     *
     * <p>플러그인 어댑터 모듈이 포함된 환경에서는 실제 구현체가 우선 주입되고,
     * 그렇지 않은 환경에서는 이 no-op 구현체가 fallback으로 사용됩니다.</p>
     *
     * <p>운영 관점에서는 플러그인 기능 비활성 상태를 식별할 수 있도록 {@code info} 로그를 남깁니다.</p>
     *
     * @return 항상 empty 를 반환하는 no-op provider
     */
    @Bean
    @ConditionalOnMissingBean(GatewaySocketPluginRuntimeProvider.class)
    public GatewaySocketPluginRuntimeProvider gatewaySocketPluginRuntimeProvider() {
        log.info("SOCKET 플러그인 런타임 제공자 미구성 상태 감지. no-op provider를 fallback으로 등록합니다.");
        return GatewaySocketPluginRuntimeProvider.noop();
    }

    /**
     * 발행 정책 엔진 Bean을 생성합니다.
     *
     * <p>{@link GatewayPublishPolicyProperties}의 설정 스펙을 읽어 코어 유스케이스가 사용하는
     * {@link PublishPolicy} 구현체로 변환합니다.</p>
     *
     * @param properties 발행 정책 설정 값
     * @return 발행 정책 엔진
     */
    @Bean
    public PublishPolicy publishPolicy(final GatewayPublishPolicyProperties properties) {
        if (log.isTraceEnabled()) {
            log.trace("공통 Bean 생성: PublishPolicy(PublishPolicyEngine). policyVersion={}", properties.getVersion());
        }
        return new PublishPolicyEngine(properties.toSpec());
    }

    /**
     * HSMS 프레임 추출기를 생성합니다.
     *
     * <p>최대 프레임 크기 제한을 설정 값으로 주입하여 메모리/CPU 보호 정책을 적용합니다.</p>
     *
     * @param hsmsProperties HSMS 설정 값
     * @return HSMS 프레임 추출기
     */
    @Bean
    public HsmsFrameExtractor hsmsFrameExtractor(final GatewayHsmsProperties hsmsProperties) {
        if (log.isTraceEnabled()) {
            log.trace("공통 Bean 생성: HsmsFrameExtractor. maxFrameBytes={}", hsmsProperties.getMaxFrameBytes());
        }
        // 운영 중 비정상 대형 프레임으로부터 런타임을 보호하기 위해 최대 프레임 크기를 강제한다.
        return new HsmsFrameExtractor(hsmsProperties.getMaxFrameBytes());
    }

    /**
     * SECS-II 디코더 기본 구현체를 생성합니다.
     *
     * @return 기본 SECS-II 디코더
     */
    @Bean
    public Secs2Decoder secs2Decoder() {
        if (log.isTraceEnabled()) {
            log.trace("공통 Bean 생성: Secs2Decoder(BasicSecs2Decoder)");
        }
        return new BasicSecs2Decoder();
    }

    /**
     * HSMS 인바운드 파이프라인을 생성합니다.
     *
     * <p>HSMS raw frame -> SECS 메시지 디코드까지의 인바운드 처리 단계를 조합합니다.</p>
     *
     * @param clockPort 시각 조회 포트
     * @param traceIdGeneratorPort TraceId 생성 포트
     * @param frameExtractor HSMS 프레임 추출기
     * @param secs2Decoder SECS-II 디코더
     * @return HSMS 인바운드 파이프라인
     */
    @Bean
    public HsmsInboundPipeline hsmsInboundPipeline(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final HsmsFrameExtractor frameExtractor,
            final Secs2Decoder secs2Decoder
    ) {
        if (log.isTraceEnabled()) {
            log.trace("공통 Bean 생성: HsmsInboundPipeline");
        }
        return new HsmsInboundPipeline(clockPort, traceIdGeneratorPort, frameExtractor, secs2Decoder);
    }

    /**
     * SOCKET 인바운드 파이프라인을 생성합니다.
     *
     * <p>SOCKET raw frame 디코드 파이프라인과 플러그인 런타임 제공자를 조합합니다.</p>
     *
     * @param clockPort 시각 조회 포트
     * @param traceIdGeneratorPort TraceId 생성 포트
     * @param pluginRuntimeProvider 설비별 플러그인 핸들러 제공자
     * @return SOCKET 인바운드 파이프라인
     */
    @Bean
    public SocketInboundPipeline socketInboundPipeline(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final GatewaySocketPluginRuntimeProvider pluginRuntimeProvider
    ) {
        if (log.isTraceEnabled()) {
            log.trace("공통 Bean 생성: SocketInboundPipeline");
        }
        return new SocketInboundPipeline(clockPort, traceIdGeneratorPort, pluginRuntimeProvider);
    }

    /**
     * SOCKET 타입 핸들러 레지스트리를 생성하고 기본 내장 핸들러를 등록합니다.
     *
     * <p>현재 기본 등록 대상은 line-delimited, regex-delimited 타입이며,
     * 추가 타입은 팀 규칙에 맞게 이 메서드 또는 별도 자동구성으로 확장할 수 있습니다.</p>
     *
     * @param socketProperties SOCKET 설정 값
     * @return 초기화된 SOCKET 타입 레지스트리
     */
    @Bean
    public SocketTypeRegistry socketTypeRegistry(final GatewaySocketProperties socketProperties) {
        final SocketTypeRegistry registry = new SocketTypeRegistry();

        // 기본 제공 socketType 핸들러를 먼저 등록한다.
        registry.register(new LineDelimitedSocketTypeHandler());
        registry.register(new RegexDelimitedSocketTypeHandler(socketProperties.getRegexEndPattern()));

        log.info("SOCKET 기본 socketType 핸들러 등록 완료. handlers=[LINE_DELIMITED, REGEX_DELIMITED], regexEndPattern={}",
                socketProperties.getRegexEndPattern());
        return registry;
    }

    // Jackson 의존성을 스타터가 강제하지 않기 위해 ObjectMapper Bean은 의도적으로 제공하지 않는다.
}
