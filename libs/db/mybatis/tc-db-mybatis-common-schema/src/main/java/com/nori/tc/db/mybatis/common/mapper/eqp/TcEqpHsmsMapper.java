package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpHsms;

/**
 * tc_eqp_hsms Mapper (FIX)
 *
 * - 1:1 테이블, PK=eqp_key
 * - created_at/updated_at은 DB default(now())를 신뢰하고 insert에서 생략한다.
 * - update 시 updated_at은 CURRENT_TIMESTAMP로 갱신한다.
 */
public interface TcEqpHsmsMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param hsms DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("h") TcEqpHsms hsms);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param hsms DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("h") TcEqpHsms hsms);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpHsms> findByEqpKey(@Param("eqpKey") long eqpKey);

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByEqpKey(@Param("eqpKey") long eqpKey);
}
