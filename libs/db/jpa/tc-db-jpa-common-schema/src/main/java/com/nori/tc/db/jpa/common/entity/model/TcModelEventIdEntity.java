package com.nori.tc.db.jpa.common.entity.model;

import com.nori.tc.db.jpa.common.entity.base.AbstractUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * tc_model_eventid 테이블 매핑 엔티티.
 *
 * [DB 스키마]
 * - event_key  : bigint PK (IDENTITY)
 * - model_key  : bigint NOT NULL (tc_model FK)
 * - event_id   : varchar(100) NOT NULL
 * - report_id  : varchar(1000) NULL
 * - enabled    : boolean NOT NULL default false
 * - updated_at : timestamptz NOT NULL
 * - Constraints: UNIQUE (model_key, event_id)
 *
 * [설계 포인트]
 * 1. MapStruct 호환성:
 * - public 기본 생성자/전체 인자 생성자를 제공합니다.
 *
 * 2. 기본값 방어:
 * - enabled가 null이면 DB 기본값(false)을 따르도록 보정합니다.
 */
@Entity
@Table(
        name = "tc_model_eventid",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tc_model_eventid_model_key_event_id", columnNames = {"model_key", "event_id"})
        }
)
public class TcModelEventIdEntity extends AbstractUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_key", nullable = false)
    private Long eventKey;

    @Column(name = "model_key", nullable = false)
    private Long modelKey;

    @Column(name = "event_id", length = 100, nullable = false)
    private String eventId;

    @Column(name = "report_id", length = 1000)
    private String reportId;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcModelEventIdEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcModelEventIdEntity(Long eventKey, Long modelKey, String eventId, String reportId, Boolean enabled) {
        this.eventKey = eventKey;
        this.modelKey = modelKey;
        this.eventId = eventId;
        this.reportId = reportId;
        this.enabled = enabled;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    public static TcModelEventIdEntity newEntity(Long modelKey, String eventId) {
        if (modelKey == null || modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be positive");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be null/blank");
        }
        TcModelEventIdEntity e = new TcModelEventIdEntity();
        e.setModelKey(modelKey);
        e.setEventId(eventId);
        return e;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @PrePersist
    private void applyDefaults() {
        if (this.enabled == null) {
            this.enabled = Boolean.FALSE;
        }
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public Long getEventKey() {
        return eventKey;
    }

    public void setEventKey(Long eventKey) {
        this.eventKey = eventKey;
    }

    public Long getModelKey() {
        return modelKey;
    }

    public void setModelKey(Long modelKey) {
        this.modelKey = modelKey;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
