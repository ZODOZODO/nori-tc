package com.nori.tc.db.jpa.common.entity.work;

import com.nori.tc.db.domain.common.work.TcWorkProcessJobState;
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
 * tc_work_processjob 테이블 매핑 엔티티.
 *
 * <p>
 * [DB 스키마]
 * - process_job_key  : bigint identity (PK)
 * - control_job_key  : bigint NOT NULL (FK)
 * - processjob_id    : varchar(64) NOT NULL
 * - processjob_state : varchar(20) NOT NULL (CREATED/QUEUED/RUNNING/PAUSED/COMPLETED/ABORTED/FAILED)
 * - recipe_id        : varchar(128) NOT NULL
 * - created_at       : timestamptz NOT NULL (AbstractCreatedUpdatedEntity 상속)
 * - updated_at       : timestamptz NOT NULL (AbstractCreatedUpdatedEntity 상속)
 * - Constraints      : UNIQUE (control_job_key, processjob_id)
 * </p>
 *
 * <p>
 * [설계 포인트]
 * 1) MapStruct 호환성:
 * - public 기본 생성자와 전체 인자 생성자를 제공한다.
 * </p>
 */
@Entity
@Table(
        name = "tc_work_processjob",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tc_work_processjob_control_job_key_processjob_id",
                        columnNames = {"control_job_key", "processjob_id"}
                )
        }
)
public class TcWorkProcessJobEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "process_job_key", nullable = false)
    private Long processJobKey;

    @Column(name = "control_job_key", nullable = false)
    private Long controlJobKey;

    @Column(name = "processjob_id", length = 64, nullable = false)
    private String processjobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "processjob_state", length = 20, nullable = false)
    private TcWorkProcessJobState processjobState;

    @Column(name = "recipe_id", length = 128, nullable = false)
    private String recipeId;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcWorkProcessJobEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcWorkProcessJobEntity(
            Long processJobKey,
            Long controlJobKey,
            String processjobId,
            TcWorkProcessJobState processjobState,
            String recipeId
    ) {
        this.processJobKey = processJobKey;
        this.controlJobKey = controlJobKey;
        this.processjobId = processjobId;
        this.processjobState = processjobState;
        this.recipeId = recipeId;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    /**
     * 유니크 키(control_job_key, processjob_id) 기반 신규 엔티티 생성.
     */
    public static TcWorkProcessJobEntity newEntity(long controlJobKey, String processjobId) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        if (processjobId == null || processjobId.isBlank()) {
            throw new IllegalArgumentException("processjobId must not be null/blank");
        }
        TcWorkProcessJobEntity e = new TcWorkProcessJobEntity();
        e.setControlJobKey(controlJobKey);
        e.setProcessjobId(processjobId);
        return e;
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public Long getProcessJobKey() {
        return processJobKey;
    }

    public void setProcessJobKey(Long processJobKey) {
        this.processJobKey = processJobKey;
    }

    public Long getControlJobKey() {
        return controlJobKey;
    }

    public void setControlJobKey(Long controlJobKey) {
        this.controlJobKey = controlJobKey;
    }

    public String getProcessjobId() {
        return processjobId;
    }

    public void setProcessjobId(String processjobId) {
        this.processjobId = processjobId;
    }

    public TcWorkProcessJobState getProcessjobState() {
        return processjobState;
    }

    public void setProcessjobState(TcWorkProcessJobState processjobState) {
        this.processjobState = processjobState;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId;
    }
}
