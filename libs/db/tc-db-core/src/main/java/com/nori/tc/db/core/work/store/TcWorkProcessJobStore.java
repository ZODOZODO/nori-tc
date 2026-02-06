package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessJob;
import com.nori.tc.db.domain.work.TcWorkProcessJob;

/**
 * tc_work_processjob CRUD 인터페이스 (기술 중립).
 *
 * <p>
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 * </p>
 */
public interface TcWorkProcessJobStore {

    /**
     * 프로세스 잡 upsert.
     *
     * <p>
     * - process_job_key가 있으면 해당 PK 기준으로 갱신한다.
     * - process_job_key가 없으면 (control_job_key, processjob_id) 유니크 키 기준으로
     *   존재 여부를 확인한 뒤 갱신/생성을 수행한다.
     * </p>
     *
     * @return upsert 후 상태의 TcWorkProcessJob
     */
    TcWorkProcessJob upsert(UpsertTcWorkProcessJob command);

    /**
     * PK(process_job_key)로 단건 조회.
     */
    Optional<TcWorkProcessJob> findByProcessJobKey(long processJobKey);

    /**
     * 유니크 키(control_job_key, processjob_id)로 단건 조회.
     */
    Optional<TcWorkProcessJob> findByControlJobKeyAndProcessjobId(long controlJobKey, String processjobId);

    /**
     * 특정 control_job_key 기준으로 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcWorkProcessJob> findAllByControlJobKey(long controlJobKey, PageRequest page);

    /**
     * PK(process_job_key) 기준 삭제.
     */
    void deleteByProcessJobKey(long processJobKey);
}
