package com.nori.tc.ui.adapters.kafka.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI Backend Kafka 토픽 이름 바인딩 설정 클래스입니다.
 *
 * <p>tc-ui-backend-app이 발행·구독하는 Kafka 토픽 이름을 프로퍼티 파일에서
 * 주입받아 발행/구독 어댑터 전반에서 일관된 토픽 이름을 사용하도록 중앙화합니다.</p>
 *
 * <p>바인딩 prefix: {@code tc.ui.backend.kafka}</p>
 *
 * <pre>
 * tc.ui.backend.kafka.gateway-events-topic=tc.ui.events.gateway
 * tc.ui.backend.kafka.business-events-topic=tc.ui.events.business
 * tc.ui.backend.kafka.commands-topic=tc.ui.commands
 * tc.ui.backend.kafka.commands-dlt-topic=tc.ui.commands.DLT
 * </pre>
 */
@ConfigurationProperties(prefix = "tc.ui.backend.kafka")
public class UiKafkaTopicProperties {

    private static final Logger log = LoggerFactory.getLogger(UiKafkaTopicProperties.class);

    /**
     * Gateway 대상 UI 이벤트 발행 토픽입니다.
     *
     * <p>eqp_create / eqp_update / eqp_delete / eqp_start / eqp_end 이벤트를
     * Gateway로 전달합니다. U13 규칙에 따라 tc_eqp.route_partition을 조회하여
     * ProducerRecord(topic, partition, key, payload) 형식으로 명시적 파티션 발행을 수행합니다.</p>
     */
    private String gatewayEventsTopic;

    /**
     * Business Core 대상 UI 이벤트 발행 토픽입니다.
     *
     * <p>eqp_create / eqp_update / eqp_delete 이벤트를 Business Core로 전달합니다.
     * eqp_start / eqp_end 는 Gateway 전담이므로 이 토픽으로 발행하지 않습니다.
     * Business 구독자는 Consumer Group 모드로 동작하므로 route_partition 지정이 불필요합니다.</p>
     */
    private String businessEventsTopic;

    /**
     * Gateway / Business Core 응답 수신 토픽입니다.
     *
     * <p>Gateway와 Business Core가 처리 결과를 이 토픽에 발행하면,
     * UiCommandKafkaSubscriber가 수신하여 eventType에 따라 분기합니다.</p>
     * <ul>
     *   <li>EQP_CREATE / EQP_UPDATE / EQP_DELETE → DualResponseRegistry</li>
     *   <li>EQP_START / EQP_END → AsyncResultStorePort (Redis)</li>
     * </ul>
     */
    private String commandsTopic;
    /**
     * tc.ui.commands 파싱 실패 메시지를 적재하는 DLT 토픽입니다.
     */
    private String commandsDltTopic = "tc.ui.commands.DLT";

    /**
     * DLT 토픽 파티션 수입니다.
     */
    private int commandsDltPartitions = 3;

    /**
     * DLT 토픽 복제 팩터입니다.
     */
    private short commandsDltReplicationFactor = 1;

    /**
     * DLT 토픽 보관 기간(ms)입니다.
     */
    private long commandsDltRetentionMs = 604_800_000L;

    /**
     * 기동 시 토픽 이름 설정 유효성을 검증합니다.
     *
     * <p>세 토픽 모두 반드시 설정되어야 합니다.
     * 누락 시 애플리케이션 기동을 중단하여 운영 오발행을 방지합니다.</p>
     */
    @PostConstruct
    public void validate() {
        requireText("tc.ui.backend.kafka.gateway-events-topic", gatewayEventsTopic);
        requireText("tc.ui.backend.kafka.business-events-topic", businessEventsTopic);
        requireText("tc.ui.backend.kafka.commands-topic", commandsTopic);
        requireText("tc.ui.backend.kafka.commands-dlt-topic", commandsDltTopic);
        if (commandsDltPartitions <= 0) {
            throw new IllegalStateException(
                    "tc.ui.backend.kafka.commands-dlt-partitions 는 1 이상이어야 합니다. actual="
                            + commandsDltPartitions
            );
        }
        if (commandsDltReplicationFactor <= 0) {
            throw new IllegalStateException(
                    "tc.ui.backend.kafka.commands-dlt-replication-factor 는 1 이상이어야 합니다. actual="
                            + commandsDltReplicationFactor
            );
        }
        if (commandsDltRetentionMs <= 0L) {
            throw new IllegalStateException(
                    "tc.ui.backend.kafka.commands-dlt-retention-ms 는 1 이상이어야 합니다. actual="
                            + commandsDltRetentionMs
            );
        }

        log.info(
                "UiKafkaTopicProperties 검증 완료. gatewayEvents={}, businessEvents={}, commands={}, commandsDlt={}, dltPartitions={}, dltReplicationFactor={}, dltRetentionMs={}",
                gatewayEventsTopic,
                businessEventsTopic,
                commandsTopic,
                commandsDltTopic,
                commandsDltPartitions,
                commandsDltReplicationFactor,
                commandsDltRetentionMs
        );
    }

    /**
     * 필수 문자열 설정값이 비어 있는지 검증합니다.
     *
     * @param key   프로퍼티 키 (예외 메시지 구분용)
     * @param value 검증 대상 값
     * @throws IllegalStateException 값이 null 또는 공백인 경우
     */
    private static void requireText(final String key, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 는 필수 설정입니다.");
        }
    }

    public String getGatewayEventsTopic() {
        return gatewayEventsTopic;
    }

    public void setGatewayEventsTopic(final String gatewayEventsTopic) {
        this.gatewayEventsTopic = gatewayEventsTopic;
    }

    public String getBusinessEventsTopic() {
        return businessEventsTopic;
    }

    public void setBusinessEventsTopic(final String businessEventsTopic) {
        this.businessEventsTopic = businessEventsTopic;
    }

    public String getCommandsTopic() {
        return commandsTopic;
    }

    public void setCommandsTopic(final String commandsTopic) {
        this.commandsTopic = commandsTopic;
    }

    public String getCommandsDltTopic() {
        return commandsDltTopic;
    }

    public void setCommandsDltTopic(final String commandsDltTopic) {
        this.commandsDltTopic = commandsDltTopic;
    }

    public int getCommandsDltPartitions() {
        return commandsDltPartitions;
    }

    public void setCommandsDltPartitions(final int commandsDltPartitions) {
        this.commandsDltPartitions = commandsDltPartitions;
    }

    public short getCommandsDltReplicationFactor() {
        return commandsDltReplicationFactor;
    }

    public void setCommandsDltReplicationFactor(final short commandsDltReplicationFactor) {
        this.commandsDltReplicationFactor = commandsDltReplicationFactor;
    }

    public long getCommandsDltRetentionMs() {
        return commandsDltRetentionMs;
    }

    public void setCommandsDltRetentionMs(final long commandsDltRetentionMs) {
        this.commandsDltRetentionMs = commandsDltRetentionMs;
    }
}
