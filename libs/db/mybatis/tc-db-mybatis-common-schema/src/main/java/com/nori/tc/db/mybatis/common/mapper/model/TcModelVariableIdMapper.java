package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.common.model.VariableIdType;
import com.nori.tc.db.domain.model.TcModelVariableId;

/**
 * tc_model_variableid Mapper (FIX)
 *
 * - 이 모듈은 "보수적인 CRUD"만 제공한다.
 * - variable_key 생성(IDENTITY) 처리 때문에 insert 후 key를 직접 반환하지 않는다.
 * - upsert는 unique(model_version_key, variable_id_type, variable_id) 기반으로 수행한다.
 */
public interface TcModelVariableIdMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVariableId 도메인 데이터 객체
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("m") TcModelVariableId modelVariableId);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVariableId 도메인 데이터 객체
     * @return DB MyBatis 계층 처리 결과
     */
    int updateByUniqueKey(@Param("m") TcModelVariableId modelVariableId);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param variableKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelVariableId> findByVariableKey(@Param("variableKey") long variableKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param variableIdType DB MyBatis 계층 처리에 사용하는 입력 값
     * @param variableId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelVariableId> findByModelVersionKeyAndTypeAndVariableId(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("variableIdType") VariableIdType variableIdType,
            @Param("variableId") String variableId
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
    List<TcModelVariableId> findAllByModelVersionKey(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param variableKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByVariableKey(@Param("variableKey") long variableKey);
}
