package com.nori.tc.db.core.eqp.store;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParamVersion;
import com.nori.tc.db.domain.eqp.TcEqpParamVersion;

import java.util.List;
import java.util.Optional;

/**
 * tc_eqp_param_version CRUD 인터페이스입니다.
 *
 * <p>버전 설명은 설비 버전 메타데이터이므로
 * tc_eqp_param과 별도 저장소 계약으로 관리합니다.</p>
 */
public interface TcEqpParamVersionStore {

    /**
     * 버전 메타데이터를 저장/갱신합니다.
     *
     * @param command 저장할 버전 메타데이터
     * @return 저장 후 최신 상태
     */
    TcEqpParamVersion upsert(UpsertTcEqpParamVersion command);

    /**
     * 설비 키와 파라미터 버전으로 버전 메타데이터를 조회합니다.
     *
     * @param eqpKey 설비 식별 키
     * @param paramVersion 파라미터 버전
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpParamVersion> findByEqpKeyAndParamVersion(long eqpKey, String paramVersion);

    /**
     * 설비 키 기준 버전 메타데이터 전체를 페이징 조회합니다.
     *
     * @param eqpKey 설비 식별 키
     * @param page 페이징 조건
     * @return 조회 결과 목록
     */
    List<TcEqpParamVersion> findAllByEqpKey(long eqpKey, PageRequest page);

    /**
     * 설비 키 기준 버전 메타데이터 전체를 삭제합니다.
     *
     * @param eqpKey 설비 식별 키
     */
    void deleteAllByEqpKey(long eqpKey);
}
