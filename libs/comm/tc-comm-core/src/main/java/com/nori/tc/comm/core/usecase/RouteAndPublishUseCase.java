package com.nori.tc.comm.core.usecase;

import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.routing.PublishDecision;
import com.nori.tc.comm.core.routing.PublishMode;
import com.nori.tc.comm.core.routing.PublishPolicy;

import java.util.Objects;

/**
 * 메시지 라우팅 결과에 따라 실제 발행을 수행하는 유스케이스입니다.
 *
 * <p>현재 시스템 정책은 {@link PublishMode#DIRECT_KAFKA} 경로만 지원합니다.
 * OUTBOX 경로가 정책에 포함되면 설정 오류로 판단해 즉시 예외를 발생시킵니다.</p>
 */
public final class RouteAndPublishUseCase {

    /**
     * 메시지별 발행 모드를 결정하는 정책 엔진입니다.
     */
    private final PublishPolicy publishPolicy;

    /**
     * Kafka 직접 발행 포트입니다.
     */
    private final KafkaPublisherPort kafkaPublisherPort;

    /**
     * 유스케이스 의존성을 초기화합니다.
     *
     * @param publishPolicy 라우팅 정책
     * @param kafkaPublisherPort Kafka 발행 포트
     */
    public RouteAndPublishUseCase(
            final PublishPolicy publishPolicy,
            final KafkaPublisherPort kafkaPublisherPort
    ) {
        this.publishPolicy = Objects.requireNonNull(publishPolicy, "publishPolicy is null");
        this.kafkaPublisherPort = Objects.requireNonNull(kafkaPublisherPort, "kafkaPublisherPort is null");
    }

    /**
     * 메시지를 라우팅한 뒤 Kafka로 발행합니다.
     *
     * @param message 파싱 완료 메시지
     * @return 정책에 의해 계산된 발행 결정 값
     * @throws Exception 발행 과정에서 발생한 예외
     */
    public PublishDecision routeAndPublish(final ParsedMessage message) throws Exception {
        Objects.requireNonNull(message, "message is null");

        final PublishDecision decision = publishPolicy.decide(message);
        if (decision.mode() != PublishMode.DIRECT_KAFKA) {
            throw new UnsupportedOperationException(
                    "Unsupported publish mode: "
                            + decision.mode()
                            + ". Configure tc.comm.gateway.publish-policy.default-mode=DIRECT_KAFKA."
            );
        }

        kafkaPublisherPort.publish(message, decision);
        return decision;
    }
}
