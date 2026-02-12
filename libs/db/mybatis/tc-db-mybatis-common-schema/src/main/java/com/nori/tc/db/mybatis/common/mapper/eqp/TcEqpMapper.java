package com.nori.tc.db.mybatis.common.mapper.eqp;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.eqp.TcEqp;

/**
 * tc_eqp Mapper (FIX)
 *
 * - eqp_id는 UNIQUE 키이므로 insert/update를 분리 제공한다.
 * - upsert(벤더별 문법)는 starter/adapter에서 흡수하는 쪽이 안전하다.
 * - [2024-XX-XX FIX] 메모리 페이징 이슈 해결을 위해 findAll에 DB 페이징 파라미터 추가
 */
public interface TcEqpMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqp 설비 식별 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("e") TcEqp eqp);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqp 설비 식별 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("e") TcEqp eqp);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcEqp> findByEqpId(@Param("eqpId") String eqpId);

    /**
     * 페이징 검색 (FIX)
     * - 기존: 메모리로 다 가져와서 자름 (OOM 위험)
     * - 변경: DB에서 잘라오도록 offset/limit 추가
     *
     * @param offset 건너뛸 행 수
     * @param limit 가져올 행 수
     */
    List<TcEqp> findAll(
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByEqpId(@Param("eqpId") String eqpId);
}
