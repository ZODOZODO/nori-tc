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

    int insert(@Param("w") TcWork work);

    int updateByWorkKey(@Param("w") TcWork work);

    Optional<TcWork> findByWorkKey(@Param("workKey") long workKey);

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

    int deleteByWorkKey(@Param("workKey") long workKey);
}
