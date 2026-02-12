package com.nori.tc.db.jpa.common.repository.eqp;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.eqp.TcEqpGlobalEntity;

/**
 * tc_eqp_global Repository
 */
public interface TcEqpGlobalJpaRepository extends JpaRepository<TcEqpGlobalEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpGlobalEntity> findByEqpKeyAndParamName(Long eqpKey, String paramName);

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회/처리 결과 목록
     */
    List<TcEqpGlobalEntity> findByEqpKey(Long eqpKey);

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB JPA 계층 처리에 사용하는 입력 값
     */
    void deleteByEqpKeyAndParamName(Long eqpKey, String paramName);
}
