package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;

/**
 * tc_model_mdf CRUD 인터페이스 (기술 중립)
 *
 * <p>
 * 구현 책임:
 * <ul>
 * <li>JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공</li>
 * <li>MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공</li>
 * </ul>
 * </p>
 *
 * 예외 정책(권장):
 * - 중복(유니크 위반 등): DbDuplicateKeyException
 * - DB 접근 실패: DbAccessException
 */
public interface TcModelMdfStore {

    /**
     * MDF upsert.
     *
     * <p>
     * - mdf_key가 있으면 해당 PK 기반으로 갱신합니다.
     * - mdf_key가 없으면 (model_key, mdf_name) 유니크 키 기준으로
     * 존재 여부를 확인한 뒤 갱신/생성을 수행합니다.
     * </p>
     *
     * @return upsert 후 상태의 TcModelMdf
     */
    TcModelMdf upsert(UpsertTcModelMdf command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param mdfKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMdf> findByMdfKey(long mdfKey);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param mdfName DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMdf> findByModelKeyAndName(long modelKey, String mdfName);

    /**
     * 특정 모델(model_key)에 연결된 MDF 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcModelMdf> findAllByModelKey(long modelKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param mdfKey 대상 키 값
     */
    void deleteByMdfKey(long mdfKey);
}