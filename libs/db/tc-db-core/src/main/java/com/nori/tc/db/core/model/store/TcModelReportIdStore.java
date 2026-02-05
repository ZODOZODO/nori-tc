package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.TcModelReportIdSearchCriteria;
import com.nori.tc.db.core.model.upsert.UpsertTcModelReportId;
import com.nori.tc.db.domain.model.TcModelReportId;

/**
 * tc_model_reportid CRUD 인터페이스 (기술 중립)
 *
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 *
 * 예외 정책(권장):
 * - 중복(유니크 위반 등): DbDuplicateKeyException
 * - DB 접근 실패: DbAccessException
 */
public interface TcModelReportIdStore {

    TcModelReportId upsert(UpsertTcModelReportId command);

    Optional<TcModelReportId> findByReportKey(long reportKey);

    Optional<TcModelReportId> findByModelKeyAndReportId(long modelKey, String reportId);

    List<TcModelReportId> findAll(TcModelReportIdSearchCriteria criteria, PageRequest page);

    void deleteByReportKey(long reportKey);
}
