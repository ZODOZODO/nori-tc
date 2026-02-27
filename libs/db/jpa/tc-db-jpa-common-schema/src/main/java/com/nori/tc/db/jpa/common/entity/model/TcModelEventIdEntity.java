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
 * - model_version_key  : bigint NOT NULL (tc_model FK)
 * - event_id   : varchar(100) NOT NULL
 * - report_id  : varchar(1000) NULL
 * - enabled    : boolean NOT NULL default false
 * - updated_at : timestamptz NOT NULL
 * - Constraints: UNIQUE (model_version_key, event_id)
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
                @UniqueConstraint(name = "uk_tc_model_eventid_model_version_key_event_id", columnNames = {"model_version_key", "event_id"})
        }
)
public class TcModelEventIdEntity extends AbstractUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_key", nullable = false)
    private Long eventKey;

    @Column(name = "model_version_key", nullable = false)
    private Long modelVersionKey;

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
    public TcModelEventIdEntity(Long eventKey, Long modelVersionKey, String eventId, String reportId, Boolean enabled) {
        this.eventKey = eventKey;
        this.modelVersionKey = modelVersionKey;
        this.eventId = eventId;
        this.reportId = reportId;
        this.enabled = enabled;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    
    /**
     * DB JPA 계층 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param eventId 처리할 이벤트 정보
     * @return DB JPA 계층 처리 결과
     */
    public static TcModelEventIdEntity newEntity(Long modelVersionKey, String eventId) {
        if (modelVersionKey == null || modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be positive");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be null/blank");
        }
        TcModelEventIdEntity e = new TcModelEventIdEntity();
        e.setModelVersionKey(modelVersionKey);
        e.setEventId(eventId);
        return e;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     */
    @PrePersist
    private void applyDefaults() {
        if (this.enabled == null) {
            this.enabled = Boolean.FALSE;
        }
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Long getEventKey() {
        return eventKey;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eventKey 대상 키 값
     */
    public void setEventKey(Long eventKey) {
        this.eventKey = eventKey;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Long getModelVersionKey() {
        return modelVersionKey;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     */
    public void setModelVersionKey(Long modelVersionKey) {
        this.modelVersionKey = modelVersionKey;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getEventId() {
        return eventId;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eventId 처리할 이벤트 정보
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getReportId() {
        return reportId;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param reportId DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public Boolean getEnabled() {
        return enabled;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param enabled DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
