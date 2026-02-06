package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkControlJob;
import com.nori.tc.db.domain.work.TcWorkControlJob;

/**
 * tc_work_controljob CRUD 인터페이스 (기술 중립).
 *
 * <p>
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 * </p>
 */
public interface TcWorkControlJobStore {

    /**
     * 컨트롤 작업(Control Job) upsert.
     *
     * <p>
     * - controlJobKey가 있으면 PK 기반으로 갱신을 시도한다.
     * - controlJobKey가 없으면 (work_key, controljob_id) 유니크 키 기준으로
     *   존재 여부를 확인한 뒤 갱신/생성을 수행한다.
     * </p>
     *
     * @return upsert 후 상태의 TcWorkControlJob
     */
    TcWorkControlJob upsert(UpsertTcWorkControlJob command);

    /**
     * PK(control_job_key) 기준 단건 조회.
     */
    Optional<TcWorkControlJob> findByControlJobKey(long controlJobKey);

    /**
     * 유니크 키(work_key, controljob_id) 기준 단건 조회.
     */
    Optional<TcWorkControlJob> findByWorkKeyAndControljobId(long workKey, String controljobId);

    /**
     * 특정 work_key에 속한 Control Job 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcWorkControlJob> findAllByWorkKey(long workKey, PageRequest page);

    /**
     * PK(control_job_key) 기준 삭제.
     */
    void deleteByControlJobKey(long controlJobKey);
}
