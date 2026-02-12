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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     * @param workLotKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkProcessjobLotMapEntity> findByProcessJobKeyAndWorkLotKey(long processJobKey, long workLotKey);
}
