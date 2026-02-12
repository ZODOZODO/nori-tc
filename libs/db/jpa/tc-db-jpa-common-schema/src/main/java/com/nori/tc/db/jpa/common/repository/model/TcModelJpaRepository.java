package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelEntity;

/**
 * tc_model Repository
 *
 * - 기본 CRUD는 JpaRepository로 충분합니다.
 * - 추후 검색 조건이 늘어나면 QueryDSL/Specification/Query method로 확장합니다.
 */
public interface TcModelJpaRepository extends JpaRepository<TcModelEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelName 도메인 데이터 객체
     * @param modelVersion 도메인 데이터 객체
     * @return 조회 결과(Optional)
     */
    Optional<TcModelEntity> findByModelNameAndModelVersion(String modelName, String modelVersion);
}
