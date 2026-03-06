package com.nori.tc.business.adapters.kafka.publish;

import com.nori.tc.business.adapters.kafka.contract.BusinessKafkaContractSupport;
import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.messaging.BusinessEqpCommandMessage;
import com.nori.tc.business.core.messaging.BusinessEqpRoutePartitionLookupPort;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BusinessEqpCommandKafkaPublisher}의 U12 고정 partition 발행 정책을 검증하는 단위 테스트입니다.
 *
 * <p>검증 목적:</p>
 * <p>1) {@code tc_eqp.route_partition} 조회 결과를 Kafka ProducerRecord의 partition으로 명시 지정하는지</p>
 * <p>2) route_partition 미배정/미존재 시 발행을 차단하는지</p>
 *
 * <p>주의:</p>
 * <p>- 본 테스트는 Kafka 브로커와 통신하지 않으며, {@link KafkaTemplate}를 Mockito로 대체합니다.</p>
 * <p>- 계약 검증 로직은 실제 {@link BusinessKafkaContractSupport}를 사용하여 U12 변경이 계약 계층과도 충돌하지 않는지 함께 확인합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BusinessEqpCommandKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private BusinessEqpRoutePartitionLookupPort routePartitionLookupPort;

    /**
     * route_partition이 존재할 때 ProducerRecord에 명시 partition이 설정되는지 검증합니다.
     *
     * <p>핵심 확인 포인트:</p>
     * <p>- topic: {@code tc.eqp.commands}</p>
     * <p>- key: {@code eqpId}</p>
     * <p>- partition: 조회된 {@code route_partition}</p>
     */
    @Test
    @DisplayName("U12: EQP command 발행 시 tc_eqp.route_partition을 Kafka partition으로 명시 지정한다")
    void shouldPublishEqpCommandWithExplicitRoutePartition() throws Exception {
        // 준비 단계: 런타임 프로퍼티/계약 검증기/조회 포트/카프카 템플릿을 구성합니다.
        final BusinessCoreRuntimeProperties runtimeProperties = new BusinessCoreRuntimeProperties();
        runtimeProperties.getKafka().setEqpCommandsTopic("tc.eqp.commands");
        runtimeProperties.getKafka().setSource("TC-BUSINESS-CORE");

        final BusinessKafkaContractSupport contractSupport = new BusinessKafkaContractSupport();
        final BusinessEqpCommandKafkaPublisher publisher = new BusinessEqpCommandKafkaPublisher(
                kafkaTemplate,
                runtimeProperties,
                contractSupport,
                routePartitionLookupPort
        );

        final BusinessEqpCommandMessage command = new BusinessEqpCommandMessage(
                "EQP_SEND_MESSAGE",
                "EQP-TEST-01",
                "TRACE-U12-001",
                "SOCKET",
                "CMD=TEST",
                "TX-001",
                Map.of("k", "v")
        );

        // 카프카 발행은 성공한 것으로 가정하고 즉시 완료 future를 반환합니다.
        when(routePartitionLookupPort.findRoutePartitionByEqpId("EQP-TEST-01")).thenReturn(Optional.of(4));
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, Object>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // 실행 단계: 발행을 수행합니다.
        publisher.publish(command);

        // 검증 단계: ProducerRecord의 핵심 라우팅 값(topic/key/partition)을 검증합니다.
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass((Class) ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        final ProducerRecord<String, Object> record = captor.getValue();
        assertEquals("tc.eqp.commands", record.topic(), "발행 토픽은 eqp command 토픽이어야 합니다.");
        assertEquals("EQP-TEST-01", record.key(), "Kafka key는 eqpId를 유지해야 합니다.");
        assertEquals(4, record.partition(), "route_partition 조회값이 Kafka partition으로 명시 지정되어야 합니다.");
    }

    /**
     * route_partition이 없으면 발행을 차단하는지 검증합니다.
     *
     * <p>U12 설계 규칙상 gateway 대상 command 토픽은 명시 partition 발행이 필수이므로,
     * route_partition 미배정 상태를 정상 처리로 허용하면 안 됩니다.</p>
     */
    @Test
    @DisplayName("U12: route_partition 미배정이면 EQP command 발행을 차단한다")
    void shouldRejectPublishWhenRoutePartitionIsMissing() {
        // 준비 단계: 발행자와 입력 메시지를 생성합니다.
        final BusinessCoreRuntimeProperties runtimeProperties = new BusinessCoreRuntimeProperties();
        runtimeProperties.getKafka().setEqpCommandsTopic("tc.eqp.commands");
        runtimeProperties.getKafka().setSource("TC-BUSINESS-CORE");

        final BusinessEqpCommandKafkaPublisher publisher = new BusinessEqpCommandKafkaPublisher(
                kafkaTemplate,
                runtimeProperties,
                new BusinessKafkaContractSupport(),
                routePartitionLookupPort
        );

        final BusinessEqpCommandMessage command = new BusinessEqpCommandMessage(
                "EQP_SEND_MESSAGE",
                "EQP-TEST-02",
                "TRACE-U12-002",
                "SOCKET",
                "CMD=TEST2",
                null,
                Map.of()
        );

        when(routePartitionLookupPort.findRoutePartitionByEqpId("EQP-TEST-02")).thenReturn(Optional.empty());

        // 실행/검증 단계: 명시 partition을 만들 수 없으므로 예외가 발생해야 하며, Kafka send는 호출되지 않아야 합니다.
        assertThrows(IllegalStateException.class, () -> publisher.publish(command));
        verify(kafkaTemplate, never()).send(org.mockito.ArgumentMatchers.<ProducerRecord<String, Object>>any());
    }
}
