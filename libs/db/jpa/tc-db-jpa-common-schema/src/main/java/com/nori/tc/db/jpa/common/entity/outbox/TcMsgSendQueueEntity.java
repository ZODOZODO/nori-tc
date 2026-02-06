package com.nori.tc.db.jpa.common.entity.outbox;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.outbox.TcMsgSendStatus;
import com.nori.tc.db.jpa.common.entity.base.AbstractCreatedUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * tc_msg_send_queue 테이블 매핑 엔티티.
 *
 * <p>
 * [DB 스키마]
 * - msg_key         : bigint identity (PK)
 * - idempotency_key : varchar(128) NOT NULL
 * - topic           : varchar(200) NOT NULL
 * - message_key     : varchar(200) NULL
 * - headers_json    : text NULL
 * - payload_json    : text NOT NULL
 * - status          : varchar(16) NOT NULL (PENDING/SENDING/SENT/FAILED/DEAD)
 * - retry_count     : int NOT NULL default 0 (>= 0)
 * - next_retry_at   : timestamptz NULL
 * - locked_by       : varchar(64) NULL
 * - locked_until    : timestamptz NULL
 * - created_at      : timestamptz NOT NULL (AbstractCreatedUpdatedEntity 상속)
 * - updated_at      : timestamptz NOT NULL (AbstractCreatedUpdatedEntity 상속)
 * - Constraints     : UNIQUE (topic, idempotency_key)
 * </p>
 *
 * <p>
 * [설계 포인트]
 * 1) MapStruct 호환성:
 * - public 기본 생성자와 전체 인자 생성자를 제공한다.
 *
 * 2) 기본값 방어:
 * - retry_count가 null이면 DB 기본값(0)에 맞춰 보정한다.
 * </p>
 */
@Entity
@Table(
        name = "tc_msg_send_queue",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tc_msg_send_queue_topic_idempotency_key",
                        columnNames = {"topic", "idempotency_key"}
                )
        }
)
public class TcMsgSendQueueEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "msg_key", nullable = false)
    private Long msgKey;

    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    @Column(name = "topic", length = 200, nullable = false)
    private String topic;

    @Column(name = "message_key", length = 200)
    private String messageKey;

    @Column(name = "headers_json", columnDefinition = "text")
    private String headersJson;

    @Column(name = "payload_json", columnDefinition = "text", nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TcMsgSendStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcMsgSendQueueEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcMsgSendQueueEntity(
            Long msgKey,
            String idempotencyKey,
            String topic,
            String messageKey,
            String headersJson,
            String payloadJson,
            TcMsgSendStatus status,
            Integer retryCount,
            OffsetDateTime nextRetryAt,
            String lockedBy,
            OffsetDateTime lockedUntil
    ) {
        this.msgKey = msgKey;
        this.idempotencyKey = idempotencyKey;
        this.topic = topic;
        this.messageKey = messageKey;
        this.headersJson = headersJson;
        this.payloadJson = payloadJson;
        this.status = status;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
        this.lockedBy = lockedBy;
        this.lockedUntil = lockedUntil;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    /**
     * 유니크 키(topic, idempotency_key) 기반 신규 엔티티 생성.
     */
    public static TcMsgSendQueueEntity newEntity(String topic, String idempotencyKey) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be null/blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be null/blank");
        }
        TcMsgSendQueueEntity e = new TcMsgSendQueueEntity();
        e.setTopic(topic);
        e.setIdempotencyKey(idempotencyKey);
        return e;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * DB Insert 전 기본값 방어.
     * - retry_count가 null이면 0으로 보정한다.
     */
    @PrePersist
    private void applyDefaults() {
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public Long getMsgKey() {
        return msgKey;
    }

    public void setMsgKey(Long msgKey) {
        this.msgKey = msgKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(String headersJson) {
        this.headersJson = headersJson;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public TcMsgSendStatus getStatus() {
        return status;
    }

    public void setStatus(TcMsgSendStatus status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(OffsetDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(OffsetDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
