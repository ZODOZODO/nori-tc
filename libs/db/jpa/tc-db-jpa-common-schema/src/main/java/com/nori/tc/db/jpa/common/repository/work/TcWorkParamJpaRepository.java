package com.nori.tc.db.jpa.common.repository.work;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkParamEntity;

/**
 * tc_work_param 전용 JPA Repository.
 *
 * <p>
 * - Unique(work_key, param_name) 기준으로 단건 조회를 지원한다.
 * - 파라미터 삭제는 기본 JpaRepository.deleteById를 사용한다.
 * </p>
 */
public interface TcWorkParamJpaRepository extends JpaRepository<TcWorkParamEntity, Long> {

    Optional<TcWorkParamEntity> findByWorkKeyAndParamName(
            long workKey,
            String paramName
    );
}
