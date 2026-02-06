package com.nori.tc.db.jpa.common.repository.work;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkProcessjobLotMapEntity;

/**
 * tc_work_processjob_lot_map JPA Repository.
 *
 * <p>
 * - PK: pj_lot_map_key
 * - Unique: (process_job_key, work_lot_key)
 * </p>
 */
public interface TcWorkProcessjobLotMapJpaRepository extends JpaRepository<TcWorkProcessjobLotMapEntity, Long> {

    Optional<TcWorkProcessjobLotMapEntity> findByProcessJobKeyAndWorkLotKey(long processJobKey, long workLotKey);
}
