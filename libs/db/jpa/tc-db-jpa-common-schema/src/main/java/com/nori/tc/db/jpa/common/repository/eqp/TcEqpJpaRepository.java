package com.nori.tc.db.jpa.common.repository.eqp;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     */
    void deleteByEqpId(String eqpId);
}
