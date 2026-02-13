package com.nori.tc.comm.gateway.config;

import com.nori.tc.comm.gateway.comm.ChannelBasedOutboundSender;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.EqpMailboxRegistry;
import com.nori.tc.comm.gateway.comm.EquipmentContextFactory;
import com.nori.tc.comm.gateway.comm.GatewayInboundPipelineRouter;
import com.nori.tc.comm.gateway.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.gateway.socket.pipeline.SocketInboundPipeline;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.port.OutboxWriterPort;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.routing.PublishPolicy;
import com.nori.tc.comm.core.usecase.EqpSequentialProcessor;
import com.nori.tc.comm.core.usecase.RouteAndPublishUseCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;

/**
 * 게이트웨이 처리 파이프라인 구성.
 *
 * - 코어 처리 흐름(라우팅/시퀀셜 처리 등)을 Bean으로 조립한다
 * - 어댑터 구현체는 각 모듈에서 주입된다
 */

/**
 * gateway 처리 파이프라인 구성
 */
/**
 * 게이트웨이 처리 파이프라인 구성.
 *
 * - 스타터의 AutoConfiguration이 @Import로 로딩한다
 * - 컴포넌트 스캔 대상이 아니므로 중복 등록을 방지한다
 */
public class GatewayProcessingConfiguration {

    /**
     * Gateway 처리 구성 로그.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewayProcessingConfiguration.class);

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public EquipmentChannelRegistry equipmentChannelRegistry() {
        return new EquipmentChannelRegistry();
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param contextFactory 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param runtimeProperties 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public EqpMailboxRegistry eqpMailboxRegistry(
            final EquipmentContextFactory contextFactory,
            final GatewayRuntimeProperties runtimeProperties
    ) {
        return new EqpMailboxRegistry(contextFactory, runtimeProperties);
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param registry 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public OutboundSenderPort outboundSenderPort(final EquipmentChannelRegistry registry) {
        return new ChannelBasedOutboundSender(registry);
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param hsmsInboundPipeline 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param socketInboundPipeline 통신 채널/세션 정보
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public InboundPipelinePort inboundPipelinePort(
            final HsmsInboundPipeline hsmsInboundPipeline,
            final SocketInboundPipeline socketInboundPipeline
    ) {
        return new GatewayInboundPipelineRouter(hsmsInboundPipeline, socketInboundPipeline);
    }

    
    /**
     * 게이트웨이 스타터 구성 입력 이벤트/요청을 처리합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param publishPolicy 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param outboxWriterPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param kafkaPublisherPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public RouteAndPublishUseCase routeAndPublishUseCase(
            final PublishPolicy publishPolicy,
            final ObjectProvider<OutboxWriterPort> outboxWriterPortProvider,
            final KafkaPublisherPort kafkaPublisherPort
    ) {
        /*
         * 임시 TODO fallback:
         * - 현재 운영 정책이 DIRECT_KAFKA만 사용하는 동안, OutboxWriterPort 구현체가 아직 없어도
         *   애플리케이션이 기동되도록 fallback 포트를 주입한다.
         * - 단, 정책/설정 오류로 OUTBOX 경로가 실행되면 즉시 실패시켜 잘못된 운영 상태를 빠르게 감지한다.
         */
        final OutboxWriterPort outboxWriterPort = outboxWriterPortProvider.getIfAvailable(() -> {
            log.warn("OutboxWriterPort bean is missing. DIRECT_KAFKA-only temporary TODO fallback will be used.");
            return (message, decision) -> {
                throw new UnsupportedOperationException(
                        "TODO: OutboxWriterPort is not implemented yet. "
                                + "Set tc.comm.gateway.publish-policy.default-mode=DIRECT_KAFKA "
                                + "or provide an OutboxWriterPort implementation."
                );
            };
        });
        return new RouteAndPublishUseCase(publishPolicy, outboxWriterPort, kafkaPublisherPort);
    }

    
    /**
     * 게이트웨이 스타터 구성 도메인 처리 로직을 수행합니다.
     *
     * <p>Spring Boot 자동 구성과 빈 조립 조건을 기준으로 처리합니다.</p>
     * @param clockPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param traceIdGeneratorPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param inboundPipelinePort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param outboundSenderPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param routeAndPublishUseCase 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param dlqPublisherPort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param quarantinePort 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @param runtimeProperties 게이트웨이 스타터 구성 처리에 사용하는 입력 값
     * @return 게이트웨이 스타터 구성 처리 결과
     */
    @Bean
    public EqpSequentialProcessor eqpSequentialProcessor(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final InboundPipelinePort inboundPipelinePort,
            final OutboundSenderPort outboundSenderPort,
            final RouteAndPublishUseCase routeAndPublishUseCase,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort,
            final GatewayRuntimeProperties runtimeProperties
    ) {
        return new EqpSequentialProcessor(
                clockPort,
                traceIdGeneratorPort,
                inboundPipelinePort,
                outboundSenderPort,
                routeAndPublishUseCase,
                dlqPublisherPort,
                quarantinePort,
                runtimeProperties.getMaxChunksPerDrain()
        );
    }
}
