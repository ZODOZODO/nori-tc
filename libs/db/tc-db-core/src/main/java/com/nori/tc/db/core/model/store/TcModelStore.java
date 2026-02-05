package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.NewTcModel;
import com.nori.tc.db.core.model.TcModelSearchCriteria;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.model.TcModel;

/**
 * tc_model CRUD 인터페이스 (기술 중립)
 *
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 *
 * 예외 정책(권장):
 * - 중복(유니크 위반 등): DbDuplicateKeyException
 * - DB 접근 실패: DbAccessException
 */
public interface TcModelStore {

    /**
     * 모델 생성.
     *
     * @return DB가 부여한 model_key 및 timestamp가 채워진 TcModel
     */
    TcModel create(NewTcModel command);

    /**
     * 모델 갱신.
     *
     * @return 갱신 후 상태의 TcModel
     */
    TcModel update(UpsertTcModel command);

    Optional<TcModel> findByModelKey(long modelKey);

    Optional<TcModel> findByNameVersion(String modelName, String modelVersion);

    /**
     * 조건 검색 + 페이징
     */
    List<TcModel> findAll(TcModelSearchCriteria criteria, PageRequest page);

    /**
     * 삭제. FK(tc_eqp.model_key)가 존재하므로, 운영 정책상 금지할 수도 있습니다.
     * (금지 정책은 상위 계층에서 통제하는 것을 권장)
     */
    void deleteByModelKey(long modelKey);
}
