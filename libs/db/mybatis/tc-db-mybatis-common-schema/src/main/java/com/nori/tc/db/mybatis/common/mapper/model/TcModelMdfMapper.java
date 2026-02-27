package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelMdf;

/**
 * tc_model_mdf Mapper (FIX)
 *
 * <p>
 * - 이 모듈은 "보수적인 CRUD"만 제공한다.
 * - mdf_key 생성(IDENTITY) 처리 때문에 insert 후 key를 직접 반환하지 않는다.
 *   → insert 후 findByModelVersionKeyAndName으로 재조회하는 방식(벤더 중립)을 권장한다.
 * - 목록 조회는 반드시 LIMIT/OFFSET으로 DB 페이징을 수행한다.
 * </p>
 */
public interface TcModelMdfMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelMdf 도메인 데이터 객체
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("m") TcModelMdf modelMdf);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelMdf 도메인 데이터 객체
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("m") TcModelMdf modelMdf);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mdfKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMdf> findByMdfKey(@Param("mdfKey") long mdfKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param mdfName DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMdf> findByModelVersionKeyAndName(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("mdfName") String mdfName
    );

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcModelMdf> findAllByModelVersionKey(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mdfKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByMdfKey(@Param("mdfKey") long mdfKey);
}
