package com.nori.tc.apps.commgateway.config;

import com.nori.tc.apps.commgateway.comm.ChannelBasedOutboundSender;
import com.nori.tc.apps.commgateway.comm.EquipmentChannelRegistry;
import com.nori.tc.apps.commgateway.comm.EqpMailboxRegistry;
import com.nori.tc.apps.commgateway.comm.EquipmentContextFactory;
import com.nori.tc.apps.commgateway.comm.GatewayInboundPipelineRouter;
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
import com.nori.tc.comm.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.socket.pipeline.SocketInboundPipeline;
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

    @Bean
    public EquipmentChannelRegistry equipmentChannelRegistry() {
        return new EquipmentChannelRegistry();
    }

    @Bean
    public EqpMailboxRegistry eqpMailboxRegistry(
            final EquipmentContextFactory contextFactory,
            final GatewayRuntimeProperties runtimeProperties
    ) {
        return new EqpMailboxRegistry(contextFactory, runtimeProperties);
    }

    @Bean
    public OutboundSenderPort outboundSenderPort(final EquipmentChannelRegistry registry) {
        return new ChannelBasedOutboundSender(registry);
    }

    @Bean
    public InboundPipelinePort inboundPipelinePort(
            final HsmsInboundPipeline hsmsInboundPipeline,
            final SocketInboundPipeline socketInboundPipeline
    ) {
        return new GatewayInboundPipelineRouter(hsmsInboundPipeline, socketInboundPipeline);
    }

    @Bean
    public RouteAndPublishUseCase routeAndPublishUseCase(
            final PublishPolicy publishPolicy,
            final OutboxWriterPort outboxWriterPort,
            final KafkaPublisherPort kafkaPublisherPort
    ) {
        return new RouteAndPublishUseCase(publishPolicy, outboxWriterPort, kafkaPublisherPort);
    }

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
