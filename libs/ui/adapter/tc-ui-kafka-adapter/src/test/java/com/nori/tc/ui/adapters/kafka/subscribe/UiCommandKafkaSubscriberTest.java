package com.nori.tc.ui.adapters.kafka.subscribe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyMessage;
import com.nori.tc.ui.adapters.kafka.config.UiKafkaPublishProperties;
import com.nori.tc.ui.adapters.kafka.config.UiKafkaTopicProperties;
import com.nori.tc.ui.core.model.UiCommandReply;
import com.nori.tc.ui.core.port.messaging.UiCommandIngressPort;
import com.nori.tc.ui.domain.task.UiTaskStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UiCommandKafkaSubscriber} 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class UiCommandKafkaSubscriberTest {

    @Mock
    private UiCommandIngressPort ingressPort;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private Acknowledgment acknowledgment;
    @Mock
    private SendResult<String, Object> sendResult;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UiCommandKafkaSubscriber subscriber;

    @BeforeEach
    void setUp() {
        final UiKafkaTopicProperties topicProperties = new UiKafkaTopicProperties();
        topicProperties.setGatewayEventsTopic("tc.ui.events.gateway");
        topicProperties.setBusinessEventsTopic("tc.ui.events.business");
        topicProperties.setCommandsTopic("tc.ui.commands");
        topicProperties.setCommandsDltTopic("tc.ui.commands.DLT");
        topicProperties.setCommandsDltPartitions(3);
        topicProperties.setCommandsDltReplicationFactor((short) 1);
        topicProperties.setCommandsDltRetentionMs(604800000L);
        topicProperties.validate();

        final UiKafkaPublishProperties publishProperties = new UiKafkaPublishProperties();
        publishProperties.setPublishTimeoutSeconds(1L);
        publishProperties.setMaxRequestBytes(1_048_576);
        publishProperties.validate();

        subscriber = new UiCommandKafkaSubscriber(
                ingressPort,
                objectMapper,
                kafkaTemplate,
                topicProperties,
                publishProperties
        );
    }

    @Test
    @DisplayName("JSON 파싱 실패 시 DLT 전송 후 ACK")
    void parsingFailure_sendDlt_andAck() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        final ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "tc.ui.commands",
                1,
                12L,
                "EQP-001",
                "{\"invalid-json\""
        );

        subscriber.onMessage(record, acknowledgment);

        final ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        verify(acknowledgment).acknowledge();
        verify(ingressPort, never()).handle(any());

        final ProducerRecord<String, Object> published = captor.getValue();
        assertEquals("tc.ui.commands.DLT", published.topic());
        assertEquals(1, published.partition());
        assertEquals("EQP-001", published.key());
        assertEquals("{\"invalid-json\"", published.value());
    }

    @Test
    @DisplayName("정상 메시지는 UiCommandReply로 변환되어 ingress로 전달되고 ACK")
    void validMessage_handleIngress_andAck() throws Exception {
        final String traceId = "trace-001";
        final KafkaUiTaskReplyMessage replyMessage = new KafkaUiTaskReplyMessage(
                new KafkaUiTaskMessage.KafkaUiTaskMetadata(
                        "EQP_START_REP",
                        OffsetDateTime.now().toString(),
                        "TC-COMM-GATEWAY",
                        traceId
                ),
                new KafkaUiTaskReplyMessage.KafkaUiTaskReplyData(
                        "EQP-001",
                        "HSMS",
                        "PASS",
                        null,
                        null
                )
        );

        final ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "tc.ui.commands",
                0,
                1L,
                "EQP-001",
                objectMapper.writeValueAsString(replyMessage)
        );

        subscriber.onMessage(record, acknowledgment);

        final ArgumentCaptor<UiCommandReply> captor = ArgumentCaptor.forClass(UiCommandReply.class);
        verify(ingressPort).handle(captor.capture());
        verify(acknowledgment).acknowledge();

        final UiCommandReply mapped = captor.getValue();
        assertEquals(traceId, mapped.traceId());
        assertEquals("TC-COMM-GATEWAY", mapped.source());
        assertEquals("EQP_START_REP", mapped.eventType());
        assertEquals("EQP-001", mapped.eqpId());
        assertEquals(UiTaskStatus.PASS, mapped.status());
        assertTrue(mapped.errorCode() == null || mapped.errorCode().isBlank());
    }
}
