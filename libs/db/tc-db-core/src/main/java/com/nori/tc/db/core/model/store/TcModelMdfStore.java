package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;

/**
 * {@code tc_model_mdf} 저장소 포트입니다.
 *
 * <p>
 * 현재 스키마 규칙은 {@code model_version_key} 기준 1:1 MDF이며,
 * 구현체(JPA/MyBatis)는 동일 규칙을 보장해야 합니다.
 * </p>
 */
public interface TcModelMdfStore {

    /**
     * MDF를 upsert 합니다.
     *
     * <p>
     * 동작 규칙은 다음과 같습니다.
     * 1) {@code mdfKey}가 있으면 PK 기준으로 갱신합니다.
     * 2) {@code mdfKey}가 없으면 {@code modelVersionKey} 기준 단건을 찾아 갱신하거나 생성합니다.
     * </p>
     *
     * @param command upsert 입력 명령
     * @return upsert 이후 상태
     */
    TcModelMdf upsert(UpsertTcModelMdf command);

    /**
     * {@code mdf_key}로 MDF를 조회합니다.
     *
     * @param mdfKey MDF PK
     * @return 조회 결과(없으면 {@link Optional#empty()})
     */
    Optional<TcModelMdf> findByMdfKey(long mdfKey);

    /**
     * {@code model_version_key}로 MDF 단건을 조회합니다.
     *
     * @param modelVersionKey 모델 버전 키
     * @return 조회 결과(없으면 {@link Optional#empty()})
     */
    Optional<TcModelMdf> findByModelVersionKey(long modelVersionKey);

    /**
     * {@code model_version_key} 기준 MDF 목록을 페이지로 조회합니다.
     *
     * <p>
     * 스키마가 1:1이어도 공통 페이징 계약 호환을 위해 목록 API를 유지합니다.
     * 실제 결과는 0건 또는 1건이어야 합니다.
     * </p>
     *
     * @param modelVersionKey 모델 버전 키
     * @param page 페이지 요청
     * @return 조회 목록
     */
    List<TcModelMdf> findAllByModelVersionKey(long modelVersionKey, PageRequest page);

    /**
     * {@code mdf_key}로 MDF를 삭제합니다.
     *
     * @param mdfKey MDF PK
     */
    void deleteByMdfKey(long mdfKey);
}
