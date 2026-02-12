package com.nori.tc.comm.core.usecase;

import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.port.OutboxWriterPort;
import com.nori.tc.comm.core.routing.PublishDecision;
import com.nori.tc.comm.core.routing.PublishMode;
import com.nori.tc.comm.core.routing.PublishPolicy;

import java.util.Objects;

/**
 * 라우팅(OUTBOX vs DIRECT_KAFKA) 후 발행을 수행하는 유스케이스
 *
 * 책임
 * - PublishPolicy로 PublishDecision을 얻는다.
 * - decision.mode에 따라 OutboxWriterPort 또는 KafkaPublisherPort를 호출한다.
 *
 * 주의
 * - 예외 처리(DLQ/Quarantine)는 이 유스케이스 바깥(EqpSequentialProcessor)에서 일관되게 처리하는 것을 권장합니다.
 *   (여기서 DLQ까지 처리하기 시작하면 에러 흐름이 분산되어 가독성이 나빠집니다.)
 */
public final class RouteAndPublishUseCase {

    private final PublishPolicy publishPolicy;
    private final OutboxWriterPort outboxWriterPort;
    private final KafkaPublisherPort kafkaPublisherPort;

    
    /**
     * 통신 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @param publishPolicy 통신 코어 모듈 처리에 사용하는 입력 값
     * @param outboxWriterPort 통신 코어 모듈 처리에 사용하는 입력 값
     * @param kafkaPublisherPort 통신 코어 모듈 처리에 사용하는 입력 값
     */
    public RouteAndPublishUseCase(
            final PublishPolicy publishPolicy,
            final OutboxWriterPort outboxWriterPort,
            final KafkaPublisherPort kafkaPublisherPort
    ) {
        // 처리 단계: 분기 조건에 따라 흐름을 제어하고 후속 작업을 호출합니다.
        this.publishPolicy = Objects.requireNonNull(publishPolicy, "publishPolicy is null");
        this.outboxWriterPort = Objects.requireNonNull(outboxWriterPort, "outboxWriterPort is null");
        this.kafkaPublisherPort = Objects.requireNonNull(kafkaPublisherPort, "kafkaPublisherPort is null");
    }

    /**
     * 메시지를 라우팅 후 발행합니다.
     *
     * @throws Exception 발행 실패 시 예외(상위에서 DLQ/격리 처리)
     */
    public PublishDecision routeAndPublish(final ParsedMessage message) throws Exception {
        // 처리 단계: 분기 조건에 따라 흐름을 제어하고 후속 작업을 호출합니다.
        Objects.requireNonNull(message, "message is null");

        final PublishDecision decision = publishPolicy.decide(message);

        if (decision.mode() == PublishMode.DIRECT_KAFKA) {
            kafkaPublisherPort.publish(message, decision);
        } else {
            // 기본값: OUTBOX (무유실 우선)
            outboxWriterPort.write(message, decision);
        }

        return decision;
    }
}
