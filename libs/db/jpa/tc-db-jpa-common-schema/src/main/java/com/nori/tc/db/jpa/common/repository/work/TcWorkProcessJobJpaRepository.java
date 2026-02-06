package com.nori.tc.db.jpa.common.repository.work;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkProcessJobEntity;

/**
 * tc_work_processjob Spring Data JPA Repository.
 */
public interface TcWorkProcessJobJpaRepository extends JpaRepository<TcWorkProcessJobEntity, Long> {

    Optional<TcWorkProcessJobEntity> findByControlJobKeyAndProcessjobId(long controlJobKey, String processjobId);
}
