package com.nori.tc.business.adapters.kafka.subscribe;

import com.nori.tc.business.core.logging.BusinessLogContext;
import com.nori.tc.business.core.runtime.BusinessTaskIngressPort;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code tc.ui.events.business} 수신 Subscriber입니다.
 *
 * <p>U1 단계에서 UI 이벤트 토픽이 Gateway/Business 경계로 분리되므로,
 * Business는 본 토픽만 구독 대상으로 사용합니다.</p>
 *
 * <p>역할:
 * 1) Kafka 원문(JSON)을 Business inbound record로 변환합니다.
 * 2) 공통 mapper를 재사용해 EQP/MES/UI 수신 경로를 일관되게 유지합니다.
 * 3) Business runtime ingress 경로로 전달합니다.</p>
 */
@Component
@ConditionalOnProperty(
        name = "tc.business.core.ui-task.kafka-listener-enabled",
        havingValue = "true"
)
/**
 * BusinessUiEventKafkaSubscriber 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */

public class BusinessUiEventKafkaSubscriber {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiEventKafkaSubscriber.class);

    private final BusinessTaskIngressPort ingressPort;
    private final BusinessKafkaInboundRecordMapper recordMapper;

    /**
     * 필수 의존성을 주입받습니다.
     *
     * @param ingressPort Business 런타임 ingress 포트
     * @param recordMapper Kafka -> Business record 변환기
     */
    public BusinessUiEventKafkaSubscriber(
            final BusinessTaskIngressPort ingressPort,
            final BusinessKafkaInboundRecordMapper recordMapper
    ) {
        this.ingressPort = Objects.requireNonNull(ingressPort, "ingressPort is null");
        this.recordMapper = Objects.requireNonNull(recordMapper, "recordMapper is null");
    }

    /**
     * UI 이벤트를 수신하여 런타임 큐에 적재합니다.
     *
     * @param record Kafka consumer record
     * @throws Exception 파싱/적재 실패
     */
    @KafkaListener(
            topics = "${tc.business.core.kafka.ui-events-topic}",
            properties = {
                    "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            }
    )
    /**
     * onMessage 기능을 수행합니다.
     *
     * @param record 입력 값
     */

    public void onMessage(final ConsumerRecord<String, String> record) throws Exception {
        final BusinessInboundRecord inboundRecord = recordMapper.map(record, BusinessMessageType.UI);
        try (BusinessLogContext ignored = BusinessLogContext.withEqpId(inboundRecord.eqpId())) {
            final boolean accepted = ingressPort.submit(inboundRecord);
            if (!accepted) {
                throw new IllegalStateException(
                        "Runtime queue overflow while ingesting UI event. topic="
                                + record.topic()
                                + ", partition="
                                + record.partition()
                                + ", offset="
                                + record.offset()
                );
            }

            if (log.isDebugEnabled()) {
                log.debug("UI event ingested. topic={}, partition={}, offset={}, eqpId={}, eventType={}",
                        inboundRecord.topic(),
                        inboundRecord.partition(),
                        inboundRecord.offset(),
                        inboundRecord.eqpId(),
                        inboundRecord.messageName());
            }
        }
    }
}
