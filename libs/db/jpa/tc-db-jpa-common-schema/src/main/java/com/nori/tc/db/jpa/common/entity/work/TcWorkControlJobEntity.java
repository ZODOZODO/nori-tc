package com.nori.tc.db.jpa.common.entity.work;

import com.nori.tc.db.domain.common.work.ControlJobState;
import com.nori.tc.db.jpa.common.entity.base.AbstractCreatedUpdatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * tc_work_controljob 테이블 매핑 엔티티.
 *
 * <p>
 * [DB 스키마]
 * - control_job_key  : bigint identity (PK)
 * - work_key         : bigint NOT NULL (FK: tc_work.work_key)
 * - controljob_id    : varchar(64) NOT NULL
 * - controljob_state : varchar(20) NOT NULL
 * - created_at       : timestamptz NOT NULL (AbstractCreatedUpdatedEntity 상속)
 * - updated_at       : timestamptz NOT NULL (AbstractCreatedUpdatedEntity 상속)
 * - Constraints      : UNIQUE (work_key, controljob_id)
 * - Check            : controljob_state IN ('CREATED', 'QUEUED', 'RUNNING', 'PAUSED', 'COMPLETED', 'ABORTED', 'FAILED')
 * </p>
 *
 * <p>
 * [설계 포인트]
 * 1) MapStruct 호환성:
 * - public 기본 생성자와 전체 인자 생성자를 제공한다.
 * 2) 상태 enum:
 * - ControlJobState enum과 DB CHECK 제약을 1:1로 매핑한다.
 * </p>
 */
@Entity
@Table(
        name = "tc_work_controljob",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tc_work_controljob_work_key_controljob_id",
                        columnNames = {"work_key", "controljob_id"}
                )
        }
)
public class TcWorkControlJobEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "control_job_key", nullable = false)
    private Long controlJobKey;

    @Column(name = "work_key", nullable = false)
    private Long workKey;

    @Column(name = "controljob_id", length = 64, nullable = false)
    private String controljobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "controljob_state", length = 20, nullable = false)
    private ControlJobState controljobState;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcWorkControlJobEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcWorkControlJobEntity(
            Long controlJobKey,
            Long workKey,
            String controljobId,
            ControlJobState controljobState
    ) {
        this.controlJobKey = controlJobKey;
        this.workKey = workKey;
        this.controljobId = controljobId;
        this.controljobState = controljobState;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    /**
     * 유니크 키(work_key, controljob_id) 기반 신규 엔티티 생성.
     */
    public static TcWorkControlJobEntity newEntity(long workKey, String controljobId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (controljobId == null || controljobId.isBlank()) {
            throw new IllegalArgumentException("controljobId must not be null/blank");
        }
        TcWorkControlJobEntity e = new TcWorkControlJobEntity();
        e.setWorkKey(workKey);
        e.setControljobId(controljobId);
        return e;
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
    public Long getControlJobKey() {
        return controlJobKey;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlJobKey 대상 키 값
     */
    public void setControlJobKey(Long controlJobKey) {
        this.controlJobKey = controlJobKey;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Long getWorkKey() {
        return workKey;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     */
    public void setWorkKey(Long workKey) {
        this.workKey = workKey;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getControljobId() {
        return controljobId;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controljobId DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setControljobId(String controljobId) {
        this.controljobId = controljobId;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public ControlJobState getControljobState() {
        return controljobState;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controljobState DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setControljobState(ControlJobState controljobState) {
        this.controljobState = controljobState;
    }
}
