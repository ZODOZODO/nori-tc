package com.nori.tc.db.jpa.common.entity.work;

import com.nori.tc.db.domain.common.work.TcWorkProcessjobLotMapOrder;
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
 * tc_work_processjob_lot_map 테이블 매핑 엔티티.
 *
 * <p>
 * [DB 스키마]
 * - pj_lot_map_key  : bigint identity (PK)
 * - process_job_key : bigint NOT NULL
 * - work_lot_key    : bigint NOT NULL
 * - map_role        : varchar(20) NULL
 * - map_order       : varchar(20) NULL (FORWARD/REVERSE)
 * - created_at      : timestamptz NOT NULL (AbstractCreatedUpdatedEntity 상속)
 * - updated_at      : timestamptz NOT NULL (AbstractCreatedUpdatedEntity 상속)
 * - Constraints     : UNIQUE (process_job_key, work_lot_key)
 * </p>
 *
 * <p>
 * [설계 포인트]
 * 1) MapStruct 호환성:
 * - public 기본 생성자와 전체 인자 생성자를 제공한다.
 *
 * 2) 체크 제약 대응:
 * - map_order는 null 또는 FORWARD/REVERSE만 허용된다.
 * </p>
 */
@Entity
@Table(
        name = "tc_work_processjob_lot_map",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tc_work_pj_lot_map_process_job_key_work_lot_key",
                        columnNames = {"process_job_key", "work_lot_key"}
                )
        }
)
public class TcWorkProcessjobLotMapEntity extends AbstractCreatedUpdatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pj_lot_map_key", nullable = false)
    private Long pjLotMapKey;

    @Column(name = "process_job_key", nullable = false)
    private Long processJobKey;

    @Column(name = "work_lot_key", nullable = false)
    private Long workLotKey;

    @Column(name = "map_role", length = 20)
    private String mapRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "map_order", length = 20)
    private TcWorkProcessjobLotMapOrder mapOrder;

    // =========================================================================
    // Constructors (MapStruct & JPA)
    // =========================================================================

    /**
     * 기본 생성자 (필수)
     */
    public TcWorkProcessjobLotMapEntity() {
    }

    /**
     * 전체 인자 생성자
     */
    public TcWorkProcessjobLotMapEntity(
            Long pjLotMapKey,
            Long processJobKey,
            Long workLotKey,
            String mapRole,
            TcWorkProcessjobLotMapOrder mapOrder
    ) {
        this.pjLotMapKey = pjLotMapKey;
        this.processJobKey = processJobKey;
        this.workLotKey = workLotKey;
        this.mapRole = mapRole;
        this.mapOrder = mapOrder;
    }

    // =========================================================================
    // Static Factory
    // =========================================================================

    /**
     * 유니크 키(process_job_key, work_lot_key) 기반 신규 엔티티 생성.
     */
    public static TcWorkProcessjobLotMapEntity newEntity(long processJobKey, long workLotKey) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        if (workLotKey <= 0) {
            throw new IllegalArgumentException("workLotKey must be > 0");
        }
        TcWorkProcessjobLotMapEntity e = new TcWorkProcessjobLotMapEntity();
        e.setProcessJobKey(processJobKey);
        e.setWorkLotKey(workLotKey);
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
    public Long getPjLotMapKey() {
        return pjLotMapKey;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param pjLotMapKey 대상 키 값
     */
    public void setPjLotMapKey(Long pjLotMapKey) {
        this.pjLotMapKey = pjLotMapKey;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Long getProcessJobKey() {
        return processJobKey;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     */
    public void setProcessJobKey(Long processJobKey) {
        this.processJobKey = processJobKey;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public Long getWorkLotKey() {
        return workLotKey;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workLotKey 대상 키 값
     */
    public void setWorkLotKey(Long workLotKey) {
        this.workLotKey = workLotKey;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public String getMapRole() {
        return mapRole;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapRole DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setMapRole(String mapRole) {
        this.mapRole = mapRole;
    }

    
    /**
     * DB JPA 계층의 현재 값을 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @return DB JPA 계층 처리 결과
     */
    public TcWorkProcessjobLotMapOrder getMapOrder() {
        return mapOrder;
    }

    
    /**
     * DB JPA 계층 설정 값을 반영합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapOrder DB JPA 계층 처리에 사용하는 입력 값
     */
    public void setMapOrder(TcWorkProcessjobLotMapOrder mapOrder) {
        this.mapOrder = mapOrder;
    }
}
