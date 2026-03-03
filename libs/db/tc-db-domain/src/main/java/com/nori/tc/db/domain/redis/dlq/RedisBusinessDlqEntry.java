package com.nori.tc.db.domain.redis.dlq;

import java.io.Serializable;
import java.util.Map;

/**
 * Business Redis(6380)에 저장되는 DLQ(Dead Letter Queue) 엔트리입니다.
 *
 * <p>공유 위치 이유:</p>
 * <p>이 클래스는 {@code tc-business-redis-adapter}가 직렬화(write)하고,
 * {@code tc-ui-redis-adapter}가 역직렬화(read)합니다.
 * JDK 직렬화는 패키지명 + 클래스명이 완전히 동일해야 역직렬화가 가능하므로,
 * 두 어댑터가 공통으로 참조할 수 있는 {@code tc-db-domain}에 위치합니다.</p>
 *
 * <p>저장 키 패턴: {@code tc:business:core:dlq:{dlqId}}</p>
 *
 * <p>직렬화 주의사항:</p>
 * <ul>
 *   <li>필드를 추가하거나 삭제하면 기존 Redis에 저장된 데이터를 읽지 못할 수 있습니다.</li>
 *   <li>호환성이 깨지는 변경이 필요한 경우 {@code serialVersionUID}를 반드시 갱신하세요.</li>
 * </ul>
 */
public class RedisBusinessDlqEntry implements Serializable {

    /**
     * JDK 직렬화 호환성 식별자입니다.
     * 필드 구조가 변경되어 하위 호환성이 깨지는 경우 이 값을 함께 변경해야 합니다.
     */
    private static final long serialVersionUID = 1L;

    /** DLQ 항목 고유 식별자입니다. */
    private String dlqId;

    /** 메시지 발행 출처입니다 (예: COMM_GATEWAY, UI_BACKEND). */
    private String source;

    /** 처리 단계입니다 (예: CONSUME, EXECUTE). */
    private String stage;

    /** 장애 발생 사유 코드입니다. */
    private String reasonCode;

    /** 장애 발생 사유 상세 메시지입니다. */
    private String reasonMessage;

    /** 장애 발생 시각 (Unix 에포크, 밀리초)입니다. */
    private long occurredAt;

    /** 원본 메시지가 수신된 Kafka 토픽입니다. */
    private String topic;

    /** 원본 메시지가 수신된 Kafka 파티션 번호입니다. */
    private Integer partition;

    /** 원본 메시지의 Kafka 오프셋입니다. */
    private Long offset;

    /** 관련 설비 식별자입니다. */
    private String eqpId;

    /** 메시지 유형입니다 (예: EQP_CREATE, EQP_START). */
    private String messageType;

    /** 메시지 이름입니다. */
    private String messageName;

    /** 요청 추적에 사용하는 트레이스 ID입니다. */
    private String traceId;

    /** 페이로드 원본 데이터를 참조하는 Redis 키입니다. */
    private String payloadRef;

    /** 추가 분류 태그 (key-value)입니다. */
    private Map<String, String> tags;

    /**
     * Redis TTL(초 단위)입니다.
     * null 이면 만료 없이 영구 보관합니다.
     */
    private Long ttlSeconds;

    /**
     * Redis 직렬화 프레임워크용 기본 생성자입니다.
     * 외부에서 직접 인스턴스를 생성하지 마세요.
     */
    protected RedisBusinessDlqEntry() {
    }

    /**
     * Business DLQ 엔트리를 생성합니다.
     *
     * @param dlqId       DLQ 항목 고유 식별자
     * @param source      메시지 발행 출처
     * @param stage       처리 단계
     * @param reasonCode  장애 사유 코드
     * @param reasonMessage 장애 사유 상세 메시지
     * @param occurredAt  장애 발생 시각 (Unix 에포크 밀리초)
     * @param topic       원본 Kafka 토픽
     * @param partition   원본 Kafka 파티션 번호
     * @param offset      원본 Kafka 오프셋
     * @param eqpId       관련 설비 식별자
     * @param messageType 메시지 유형
     * @param messageName 메시지 이름
     * @param traceId     요청 추적 ID
     * @param payloadRef  페이로드 참조 Redis 키
     * @param tags        추가 분류 태그
     * @param ttlSeconds  Redis TTL(초), null 이면 영구 보관
     */
    public RedisBusinessDlqEntry(
            final String dlqId,
            final String source,
            final String stage,
            final String reasonCode,
            final String reasonMessage,
            final long occurredAt,
            final String topic,
            final Integer partition,
            final Long offset,
            final String eqpId,
            final String messageType,
            final String messageName,
            final String traceId,
            final String payloadRef,
            final Map<String, String> tags,
            final Long ttlSeconds
    ) {
        this.dlqId = dlqId;
        this.source = source;
        this.stage = stage;
        this.reasonCode = reasonCode;
        this.reasonMessage = reasonMessage;
        this.occurredAt = occurredAt;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.eqpId = eqpId;
        this.messageType = messageType;
        this.messageName = messageName;
        this.traceId = traceId;
        this.payloadRef = payloadRef;
        this.tags = tags;
        this.ttlSeconds = ttlSeconds;
    }

    public String getDlqId() {
        return dlqId;
    }

    public String getSource() {
        return source;
    }

    public String getStage() {
        return stage;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonMessage() {
        return reasonMessage;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public String getTopic() {
        return topic;
    }

    public Integer getPartition() {
        return partition;
    }

    public Long getOffset() {
        return offset;
    }

    public String getEqpId() {
        return eqpId;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getMessageName() {
        return messageName;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getPayloadRef() {
        return payloadRef;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Long getTtlSeconds() {
        return ttlSeconds;
    }
}
