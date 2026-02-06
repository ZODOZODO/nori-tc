package com.nori.tc.db.jpa.common.repository.work;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkControlJobEntity;

/**
 * tc_work_controljob JPA Repository.
 */
public interface TcWorkControlJobJpaRepository extends JpaRepository<TcWorkControlJobEntity, Long> {

    /**
     * 유니크 키(work_key, controljob_id) 기준 단건 조회.
     */
    Optional<TcWorkControlJobEntity> findByWorkKeyAndControljobId(long workKey, String controljobId);
}
