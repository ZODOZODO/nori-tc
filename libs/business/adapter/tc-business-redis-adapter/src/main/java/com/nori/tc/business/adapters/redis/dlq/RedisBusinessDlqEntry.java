package com.nori.tc.business.adapters.redis.dlq;

import java.io.Serializable;
import java.util.Map;

/**
 * Redis 저장용 Business DLQ 엔트리입니다.
 *
 * <p>Redis 키 1개에 DLQ 레코드 1건을 저장하며,
 * 운영 조회를 위한 핵심 메타데이터를 그대로 포함합니다.</p>
 */
public class RedisBusinessDlqEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String dlqId;
    private String source;
    private String stage;
    private String reasonCode;
    private String reasonMessage;
    private long occurredAt;
    private String topic;
    private Integer partition;
    private Long offset;
    private String eqpId;
    private String messageType;
    private String messageName;
    private String traceId;
    private String payloadRef;
    private Map<String, String> tags;
    private Long ttlSeconds;

    /**
     * Redis 직렬화 프레임워크용 기본 생성자입니다.
     */
    protected RedisBusinessDlqEntry() {
    }

    /**
     * 저장용 엔트리를 생성합니다.
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
