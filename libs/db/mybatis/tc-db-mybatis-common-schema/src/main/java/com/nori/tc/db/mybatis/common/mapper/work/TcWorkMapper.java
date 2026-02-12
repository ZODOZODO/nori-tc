package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWork;

/**
 * tc_work Mapper (FIX)
 *
 * - work_key 생성(IDENTITY) 처리 때문에 insert 후 key를 직접 반환하지 않는다.
 * - 벤더 중립성을 위해 insert 후 유니크 키로 재조회하는 방식을 권장한다.
 */
public interface TcWorkMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param work DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("w") TcWork work);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param work DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int updateByWorkKey(@Param("w") TcWork work);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWork> findByWorkKey(@Param("workKey") long workKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param workId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWork> findByEqpKeyAndWorkId(
            @Param("eqpKey") long eqpKey,
            @Param("workId") String workId
    );

    /**
     * 특정 설비(eqp_key)의 작업 목록 조회.
     * - DB 페이징 적용
     */
    List<TcWork> findAllByEqpKey(
            @Param("eqpKey") long eqpKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByWorkKey(@Param("workKey") long workKey);
}
