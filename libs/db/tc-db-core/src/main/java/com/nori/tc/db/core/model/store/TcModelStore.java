package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
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
     * 모델 upsert.
     *
     * <p>
     * - model_key가 있으면 해당 PK 기반으로 갱신합니다.
     * - model_key가 없으면 (model_name, model_version) 유니크 키 기준으로
     * 존재 여부를 확인한 뒤 갱신/생성을 수행합니다.
     * </p>
     *
     * @return upsert 후 상태의 TcModel
     */
    TcModel upsert(UpsertTcModel command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModel> findByModelKey(long modelKey);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param modelName 도메인 데이터 객체
     * @param modelVersion 도메인 데이터 객체
     * @return 조회 결과(Optional)
     */
    Optional<TcModel> findByNameVersion(String modelName, String modelVersion);

    /**
     * 목록 조회 + 페이징
     */
    List<TcModel> findAll(PageRequest page);

    /**
     * 삭제. FK(tc_eqp.model_key)가 존재하므로, 운영 정책상 금지할 수도 있습니다.
     * (금지 정책은 상위 계층에서 통제하는 것을 권장)
     */
    void deleteByModelKey(long modelKey);
}
