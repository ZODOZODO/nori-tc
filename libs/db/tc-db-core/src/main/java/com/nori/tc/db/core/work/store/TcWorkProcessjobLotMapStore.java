package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessjobLotMap;
import com.nori.tc.db.domain.work.TcWorkProcessjobLotMap;

/**
 * tc_work_processjob_lot_map CRUD 인터페이스 (기술 중립).
 *
 * <p>
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 * </p>
 */
public interface TcWorkProcessjobLotMapStore {

    /**
     * 공정 작업-LOT 매핑 upsert.
     *
     * <p>
     * - pjLotMapKey가 있으면 해당 PK 기반으로 갱신을 시도한다.
     * - pjLotMapKey가 없으면 (process_job_key, work_lot_key) 유니크 키 기준으로
     *   존재 여부를 확인한 뒤 갱신/생성을 수행한다.
     * </p>
     *
     * @return upsert 후 상태의 TcWorkProcessjobLotMap
     */
    TcWorkProcessjobLotMap upsert(UpsertTcWorkProcessjobLotMap command);

    /**
     * PK(pj_lot_map_key)로 단건 조회.
     */
    Optional<TcWorkProcessjobLotMap> findByPjLotMapKey(long pjLotMapKey);

    /**
     * 유니크 키(process_job_key, work_lot_key)로 단건 조회.
     */
    Optional<TcWorkProcessjobLotMap> findByProcessJobKeyAndWorkLotKey(long processJobKey, long workLotKey);

    /**
     * 특정 process_job_key에 속한 매핑 목록 조회.
     *
     * <p>
     * 페이징은 반드시 DB 레벨에서 처리해야 한다.
     * </p>
     */
    List<TcWorkProcessjobLotMap> findAllByProcessJobKey(long processJobKey, PageRequest page);

    /**
     * PK(pj_lot_map_key) 기준 삭제.
     */
    void deleteByPjLotMapKey(long pjLotMapKey);
}
