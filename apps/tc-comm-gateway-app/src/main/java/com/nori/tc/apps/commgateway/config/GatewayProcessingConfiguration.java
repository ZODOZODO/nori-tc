package com.nori.tc.apps.commgateway.config;

import com.nori.tc.apps.commgateway.comm.ChannelBasedOutboundSender;
import com.nori.tc.apps.commgateway.comm.EquipmentChannelRegistry;
import com.nori.tc.apps.commgateway.comm.GatewayInboundPipelineRouter;
import com.nori.tc.apps.commgateway.comm.PerEquipmentExecutor;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.port.OutboxWriterPort;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceNoGeneratorPort;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.routing.PublishPolicy;
import com.nori.tc.comm.core.usecase.EqpSequentialProcessor;
import com.nori.tc.comm.core.usecase.RouteAndPublishUseCase;
import com.nori.tc.comm.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.socket.pipeline.SocketInboundPipeline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gateway 처리 파이프라인 구성
 */
@Configuration
public class GatewayProcessingConfiguration {

    @Bean
    public EquipmentChannelRegistry equipmentChannelRegistry() {
        return new EquipmentChannelRegistry();
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
            final TraceNoGeneratorPort traceNoGeneratorPort,
            final InboundPipelinePort inboundPipelinePort,
            final OutboundSenderPort outboundSenderPort,
            final RouteAndPublishUseCase routeAndPublishUseCase,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort,
            final GatewayRuntimeProperties runtimeProperties
    ) {
        return new EqpSequentialProcessor(
                clockPort,
                traceNoGeneratorPort,
                inboundPipelinePort,
                outboundSenderPort,
                routeAndPublishUseCase,
                dlqPublisherPort,
                quarantinePort,
                runtimeProperties.getMaxChunksPerDrain()
        );
    }

    @Bean
    public PerEquipmentExecutor perEquipmentExecutor(final GatewayRuntimeProperties runtimeProperties) {
        return new PerEquipmentExecutor(runtimeProperties.getWorkerThreads());
    }
}
