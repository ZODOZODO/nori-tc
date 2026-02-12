package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpParam;

/**
 * tc_eqp_param Mapper (FIX)
 *
 * - Unique(eqp_key, param_name, param_version) 기준으로 upsert 지원
 * - findAllByEqpKey는 반드시 DB 페이징을 적용한다.
 */
public interface TcEqpParamMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param param DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("e") TcEqpParam param);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param param DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int updateByUniqueKey(@Param("e") TcEqpParam param);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB MyBatis 계층 처리에 사용하는 입력 값
     * @param paramVersion DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpParam> findByEqpKeyAndNameVersion(
            @Param("eqpKey") long eqpKey,
            @Param("paramName") String paramName,
            @Param("paramVersion") String paramVersion
    );

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcEqpParam> findAllByEqpKey(
            @Param("eqpKey") long eqpKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpParamKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByEqpParamKey(@Param("eqpParamKey") long eqpParamKey);
}
