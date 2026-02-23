package com.nori.tc.comm.gateway.starter.autoconfigure;

import com.nori.tc.comm.gateway.runtime.channel.ChannelBasedOutboundSender;
import com.nori.tc.comm.gateway.runtime.channel.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.runtime.mailbox.EquipmentMailboxRegistry;
import com.nori.tc.comm.gateway.runtime.context.EquipmentRuntimeContextFactory;
import com.nori.tc.comm.gateway.application.routing.ProtocolInboundPipelineRouter;
import com.nori.tc.comm.gateway.config.props.GatewayRuntimeProperties;
import com.nori.tc.comm.gateway.hsms.pipeline.HsmsInboundPipeline;
import com.nori.tc.comm.gateway.socket.pipeline.SocketInboundPipeline;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.InboundPipelinePort;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.core.routing.PublishPolicy;
import com.nori.tc.comm.core.usecase.EqpSequentialProcessor;
import com.nori.tc.comm.core.usecase.RouteAndPublishUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;

/**
 * Gateway 처리 파이프라인을 조립하는 Spring 구성 클래스입니다.
 *
 * <p>이 클래스는 코어 유스케이스와 어댑터 구현체를 연결해
 * 런타임 처리 흐름을 실행 가능한 빈으로 노출합니다.</p>
 */
public class GatewayProcessingConfiguration {

    /**
     * 구성 단계 로그를 위한 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewayProcessingConfiguration.class);

    /**
     * 설비 채널 레지스트리 빈을 생성합니다.
     *
     * @return 채널 레지스트리
     */
    @Bean
    public EquipmentChannelRegistry equipmentChannelRegistry() {
        if (log.isDebugEnabled()) {
            log.debug("처리 파이프라인 Bean 생성: EquipmentChannelRegistry");
        }
        return new EquipmentChannelRegistry();
    }

    /**
     * 설비별 메일박스 레지스트리를 생성합니다.
     *
     * @param contextFactory 설비 컨텍스트 팩토리
     * @param runtimeProperties 게이트웨이 런타임 설정
     * @return 설비 메일박스 레지스트리
     */
    @Bean
    public EquipmentMailboxRegistry eqpMailboxRegistry(
            final EquipmentRuntimeContextFactory contextFactory,
            final GatewayRuntimeProperties runtimeProperties
    ) {
        if (log.isDebugEnabled()) {
            log.debug("처리 파이프라인 Bean 생성: EquipmentMailboxRegistry. inboundQueueCapacity={}, outboundQueueCapacity={}",
                    runtimeProperties.getInboundQueueCapacity(),
                    runtimeProperties.getOutboundQueueCapacity());
        }
        return new EquipmentMailboxRegistry(contextFactory, runtimeProperties);
    }

    /**
     * 아웃바운드 송신 포트를 생성합니다.
     *
     * @param registry 채널 레지스트리
     * @return 아웃바운드 송신 포트
     */
    @Bean
    public OutboundSenderPort outboundSenderPort(final EquipmentChannelRegistry registry) {
        if (log.isDebugEnabled()) {
            log.debug("처리 파이프라인 Bean 생성: OutboundSenderPort(ChannelBasedOutboundSender)");
        }
        return new ChannelBasedOutboundSender(registry);
    }

    /**
     * 프로토콜별 인바운드 파이프라인 라우터를 생성합니다.
     *
     * @param hsmsInboundPipeline HSMS 인바운드 파이프라인
     * @param socketInboundPipeline SOCKET 인바운드 파이프라인
     * @return 인바운드 파이프라인 포트
     */
    @Bean
    public InboundPipelinePort inboundPipelinePort(
            final HsmsInboundPipeline hsmsInboundPipeline,
            final SocketInboundPipeline socketInboundPipeline
    ) {
        if (log.isDebugEnabled()) {
            log.debug("처리 파이프라인 Bean 생성: InboundPipelinePort(ProtocolInboundPipelineRouter)");
        }
        return new ProtocolInboundPipelineRouter(hsmsInboundPipeline, socketInboundPipeline);
    }

    /**
     * 라우팅 후 발행 유스케이스를 생성합니다.
     *
     * <p>현재 운영 정책은 DIRECT_KAFKA 전용이며,
     * OUTBOX 모드는 정책 단계에서 차단됩니다.</p>
     *
     * @param publishPolicy 발행 정책
     * @param kafkaPublisherPort Kafka 발행 포트
     * @return 라우팅/발행 유스케이스
     */
    @Bean
    public RouteAndPublishUseCase routeAndPublishUseCase(
            final PublishPolicy publishPolicy,
            final KafkaPublisherPort kafkaPublisherPort
    ) {
        if (log.isDebugEnabled()) {
            log.debug("RouteAndPublishUseCase bean created. mode=direct-kafka-only");
        }
        return new RouteAndPublishUseCase(publishPolicy, kafkaPublisherPort);
    }

    /**
     * 설비 단위 순차 처리기를 생성합니다.
     *
     * @param clockPort 시각 포트
     * @param traceIdGeneratorPort TraceId 생성 포트
     * @param inboundPipelinePort 인바운드 파이프라인 포트
     * @param outboundSenderPort 아웃바운드 송신 포트
     * @param routeAndPublishUseCase 라우팅/발행 유스케이스
     * @param dlqPublisherPort DLQ 발행 포트
     * @param quarantinePort 격리 포트
     * @param runtimeProperties 런타임 설정
     * @return 설비 순차 처리기
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
        // 설비 단위 드레인 루프에서 한 번에 처리할 청크 수 상한을 런타임 설정으로 주입한다.
        final int maxChunksPerDrain = runtimeProperties.getMaxChunksPerDrain();

        log.info("EqpSequentialProcessor Bean 생성. maxChunksPerDrain={}", maxChunksPerDrain);
        if (log.isDebugEnabled()) {
            log.debug("처리 파이프라인 Bean 생성: EqpSequentialProcessor");
        }

        return new EqpSequentialProcessor(
                clockPort,
                traceIdGeneratorPort,
                inboundPipelinePort,
                outboundSenderPort,
                routeAndPublishUseCase,
                dlqPublisherPort,
                quarantinePort,
                maxChunksPerDrain
        );
    }
}
