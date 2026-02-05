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

    Optional<TcModelEntity> findByModelNameAndModelVersion(String modelName, String modelVersion);
}
