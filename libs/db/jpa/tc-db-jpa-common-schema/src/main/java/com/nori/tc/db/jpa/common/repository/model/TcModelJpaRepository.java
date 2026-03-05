package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelEntity;

/**
 * {@code tc_model} 원장 테이블 전용 JPA Repository입니다.
 *
 * <p>
 * 본 Repository는 {@code tc_model} 실 컬럼 기준 최소 조회만 제공합니다.
 * 버전 컬럼({@code model_version}, {@code status})은 {@code tc_model_version}에 있으므로
 * 본 인터페이스에서는 버전 기준 파생 쿼리를 제공하지 않습니다.
 * </p>
 */
public interface TcModelJpaRepository extends JpaRepository<TcModelEntity, Long> {

    /**
     * 모델 이름으로 {@code tc_model} 원장 행을 조회합니다.
     *
     * @param modelName 모델 이름
     * @return 조회 결과
     */
    Optional<TcModelEntity> findByModelName(String modelName);
}
