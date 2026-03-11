package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.model.TcModel;

/**
 * tc_model 계열 조회/업서트/삭제 추상화 인터페이스입니다.
 *
 * <p>
 * 구현체는 기술 스택(JPA/MyBatis)에 무관하게 동일한 동작 계약을 제공해야 합니다.
 * 본 계약은 tc_model 원장 + tc_model_version 버전 테이블 조인 관점을 기준으로 합니다.
 * </p>
 *
 * <p>
 * 예외 매핑 권장:
 * - 중복/유니크 위반: DbDuplicateKeyException
 * - 그 외 DB 접근 실패: DbAccessException
 * </p>
 */
public interface TcModelStore {

    /**
     * 모델 업서트를 수행합니다.
     *
     * <p>
     * 호환성 규칙(중요):
     * {@link UpsertTcModel#modelKey()}는 "model_key"가 아니라
     * "model_version_key" 의미로 해석합니다.
     * </p>
     *
     * @param command 업서트 입력
     * @return 저장 후 모델 스냅샷
     */
    TcModel upsert(UpsertTcModel command);

    /**
     * model_version_key 기준 단건 조회입니다.
     *
     * @param modelVersionKey 모델 버전 키
     * @return 조회 결과
     */
    Optional<TcModel> findByModelVersionKey(long modelVersionKey);

    /**
     * (model_name, model_version) 기준 단건 조회입니다.
     *
     * @param modelName 모델 이름
     * @param modelVersion 모델 버전 문자열
     * @return 조회 결과
     */
    Optional<TcModel> findByNameVersion(String modelName, String modelVersion);

    /**
     * 페이지 기반 목록 조회입니다.
     *
     * @param page 페이지 요청
     * @return 모델 목록
     */
    List<TcModel> findAll(PageRequest page);

    /**
     * model_version_key 기준 삭제입니다.
     *
     * @param modelVersionKey 모델 버전 키
     */
    void deleteByModelVersionKey(long modelVersionKey);

    /**
     * model_key 기준으로 모델 원장을 삭제합니다.
     *
     * <p>{@code tc_model_version}와 상세 하위 테이블은 FK ON DELETE CASCADE로 함께 삭제됩니다.</p>
     *
     * @param modelKey 모델 키
     */
    void deleteByModelKey(long modelKey);
}
