package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelMdfEntity;

/**
 * tc_model_mdf Repository
 *
 * <p>
 * - 기본 CRUD는 JpaRepository로 충분합니다.
 * - (model_key, mdf_name) 조합 조회용 메서드를 제공합니다.
 * </p>
 */
public interface TcModelMdfJpaRepository extends JpaRepository<TcModelMdfEntity, Long> {

    Optional<TcModelMdfEntity> findByModelKeyAndMdfName(Long modelKey, String mdfName);

}
