package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * model_version_key 기준으로 모델(버전 포함 뷰)을 조회합니다.
     */
    @Query(
            value = """
                    SELECT m.model_key,
                           m.model_name,
                           mv.model_version AS model_version,
                           m.comm_interface,
                           mv.status AS status,
                           m.maker,
                           m.created_at,
                           m.updated_at,
                           m.created_by,
                           m.updated_by
                      FROM tc_model m
                      JOIN tc_model_version mv ON mv.model_key = m.model_key
                     WHERE mv.model_version_key = :modelVersionKey
                    """,
            nativeQuery = true
    )
    Optional<TcModelEntity> findByModelVersionKey(@Param("modelVersionKey") long modelVersionKey);

    /**
     * model_version_key 기준으로 버전 행을 삭제합니다.
     */
    @Modifying
    @Query(value = "DELETE FROM tc_model_version WHERE model_version_key = :modelVersionKey", nativeQuery = true)
    void deleteByModelVersionKey(@Param("modelVersionKey") long modelVersionKey);
}
