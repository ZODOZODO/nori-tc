package com.nori.tc.comm.gateway.starter.autoconfigure;

import com.nori.tc.comm.gateway.config.props.GatewayKafkaShardProperties;
import com.nori.tc.comm.gateway.config.props.GatewayNettyProperties;
import com.nori.tc.comm.gateway.config.props.GatewayRuntimeProperties;
import com.nori.tc.comm.gateway.observability.logging.GatewayObservationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * tc-comm-gateway starter auto configuration entrypoint.
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.nori.tc.comm")
@Import({
        GatewayCommConfiguration.class,
        GatewayProcessingConfiguration.class
})
public class TcCommGatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TcCommGatewayAutoConfiguration.class);

    public TcCommGatewayAutoConfiguration() {
        log.info("GW_BOOT_STARTING. imports=[GatewayCommConfiguration, GatewayProcessingConfiguration]");
    }

    /**
     * Emits a compact startup summary so operators can immediately confirm runtime shape.
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> gatewayStartupReadyObservationListener(
            final GatewayRuntimeProperties runtimeProperties,
            final GatewayNettyProperties nettyProperties,
            final GatewayKafkaShardProperties shardProperties,
            final Environment environment
    ) {
        return event -> {
            log.info("GW_BOOT_COMPONENT_READY. component=KafkaTopics");
            log.info("GW_BOOT_COMPONENT_READY. component=NettyRuntime");
            log.info("GW_BOOT_COMPONENT_READY. component=GatewayProcessingRuntime");

            final String appName = event.getApplicationContext()
                    .getEnvironment()
                    .getProperty("spring.application.name", "tc-comm-gateway-app");

            // Starter 계층은 Kafka contract 타입에 직접 결합하지 않고, 최종 바인딩된 환경값으로
            // 관측용 startup 요약 문자열만 구성합니다. 이 방식은 starter compile classpath를 단순화하고,
            // 관측 로그 목적(읽기 쉬운 구성 요약)에는 충분합니다.
            final String consumeTopics = joinTopics(
                    environment.getProperty("tc.messaging.kafka.topic.eqp-commands"),
                    environment.getProperty("tc.messaging.kafka.topic.ui-events")
            );
            final String produceTopics = joinTopics(
                    environment.getProperty("tc.messaging.kafka.topic.eqp-events"),
                    environment.getProperty("tc.messaging.kafka.topic.ui-commands")
            );

            GatewayObservationLogger.logBootReady(
                    appName,
                    "HSMS,SOCKET",
                    consumeTopics,
                    produceTopics,
                    runtimeProperties.getWorkerThreads(),
                    nettyProperties.getBossThreads(),
                    nettyProperties.getWorkerThreads(),
                    String.valueOf(shardProperties.getOwnedPartitions()),
                    runtimeProperties.getInboundQueueCapacity(),
                    runtimeProperties.getOutboundQueueCapacity()
            );
        };
    }

    /**
     * Null/blank 안전하게 startup 요약용 topic 문자열을 합칩니다.
     *
     * <p>운영자가 로그 한 줄에서 토픽 구성 여부를 빠르게 확인하는 것이 목적이므로,
     * 값이 비어 있으면 {@code N/A}로 표기해 누락을 즉시 식별할 수 있게 합니다.</p>
     */
    private static String joinTopics(final String first, final String second) {
        final String left = sanitizeTopic(first);
        final String right = sanitizeTopic(second);
        return left + "," + right;
    }

    /**
     * startup summary 로그용 topic 표기 문자열을 정규화합니다.
     */
    private static String sanitizeTopic(final String value) {
        if (value == null) {
            return "N/A";
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? "N/A" : trimmed;
    }
}
