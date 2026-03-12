package com.nori.tc.db.jpa.common.repository.eqp;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nori.tc.db.jpa.common.entity.eqp.TcEqpEntity;

/**
 * tc_eqp Repository
 */
public interface TcEqpJpaRepository extends JpaRepository<TcEqpEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpEntity> findByEqpId(String eqpId);

    /**
     * 설비 단위 경쟁 구간 직렬화를 위해 tc_eqp 행을 배타 잠금으로 조회합니다.
     *
     * @param eqpId 설비 식별 정보
     * @return 잠금 조회 결과
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM TcEqpEntity e WHERE e.eqpId = :eqpId")
    Optional<TcEqpEntity> findByEqpIdForUpdate(@Param("eqpId") String eqpId);

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     */
    void deleteByEqpId(String eqpId);
}
