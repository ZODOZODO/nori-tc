package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqpStateHist;

/**
 * tc_eqp_state_hist Mapper (FIX)
 */
@Mapper
public interface TcEqpStateHistMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param history DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("h") TcEqpStateHist history);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param limit 페이징/조회 범위 조건
     * @param offset 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcEqpStateHist> findAllByEqpKey(
            @Param("eqpKey") long eqpKey,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}
