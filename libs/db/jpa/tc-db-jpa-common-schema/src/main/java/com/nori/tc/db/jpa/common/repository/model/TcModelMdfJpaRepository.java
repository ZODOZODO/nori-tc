package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelMdfEntity;

/**
 * {@code tc_model_mdf} JPA Repository입니다.
 */
public interface TcModelMdfJpaRepository extends JpaRepository<TcModelMdfEntity, Long> {

    /**
     * {@code model_version_key} 기준 MDF 단건을 조회합니다.
     *
     * @param modelVersionKey 모델 버전 키
     * @return 조회 결과(없으면 empty)
     */
    Optional<TcModelMdfEntity> findByModelVersionKey(Long modelVersionKey);
}
