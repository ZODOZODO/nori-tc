package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.NewTcModelMdf;
import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;

/**
 * tc_model_mdf CRUD 인터페이스 (기술 중립)
 *
 * <p>
 * 구현 책임:
 * <ul>
 *   <li>JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공</li>
 *   <li>MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공</li>
 * </ul>
 * </p>
 *
 * 예외 정책(권장):
 * - 중복(유니크 위반 등): DbDuplicateKeyException
 * - DB 접근 실패: DbAccessException
 */
public interface TcModelMdfStore {

    /**
     * MDF 생성.
     *
     * @return DB가 부여한 mdf_key 및 updated_at이 채워진 TcModelMdf
     */
    TcModelMdf create(NewTcModelMdf command);

    /**
     * MDF 갱신.
     *
     * @return 갱신 후 상태의 TcModelMdf
     */
    TcModelMdf update(UpsertTcModelMdf command);

    Optional<TcModelMdf> findByMdfKey(long mdfKey);

    Optional<TcModelMdf> findByModelKeyAndName(long modelKey, String mdfName);

    /**
     * 특정 모델(model_key)에 연결된 MDF 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcModelMdf> findAllByModelKey(long modelKey, PageRequest page);

    void deleteByMdfKey(long mdfKey);
}
