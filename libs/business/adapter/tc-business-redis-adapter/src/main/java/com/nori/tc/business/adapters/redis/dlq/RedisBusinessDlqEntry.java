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

    /**
     * getDlqId 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getDlqId() {
        return dlqId;
    }

    /**
     * getSource 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getSource() {
        return source;
    }

    /**
     * getStage 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getStage() {
        return stage;
    }

    /**
     * getReasonCode 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getReasonCode() {
        return reasonCode;
    }

    /**
     * getReasonMessage 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getReasonMessage() {
        return reasonMessage;
    }

    /**
     * getOccurredAt 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getOccurredAt() {
        return occurredAt;
    }

    /**
     * getTopic 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getTopic() {
        return topic;
    }

    /**
     * getPartition 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Integer getPartition() {
        return partition;
    }

    /**
     * getOffset 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Long getOffset() {
        return offset;
    }

    /**
     * getEqpId 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getEqpId() {
        return eqpId;
    }

    /**
     * getMessageType 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getMessageType() {
        return messageType;
    }

    /**
     * getMessageName 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getMessageName() {
        return messageName;
    }

    /**
     * getTraceId 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getTraceId() {
        return traceId;
    }

    /**
     * getPayloadRef 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getPayloadRef() {
        return payloadRef;
    }

    /**
     * getTags 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * getTtlSeconds 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Long getTtlSeconds() {
        return ttlSeconds;
    }
}
