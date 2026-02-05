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
 *   → insert 후 findByModelKeyAndName으로 재조회하는 방식(벤더 중립)을 권장한다.
 * - 목록 조회는 반드시 LIMIT/OFFSET으로 DB 페이징을 수행한다.
 * </p>
 */
public interface TcModelMdfMapper {

    int insert(@Param("m") TcModelMdf modelMdf);

    int update(@Param("m") TcModelMdf modelMdf);

    Optional<TcModelMdf> findByMdfKey(@Param("mdfKey") long mdfKey);

    Optional<TcModelMdf> findByModelKeyAndName(
            @Param("modelKey") long modelKey,
            @Param("mdfName") String mdfName
    );

    List<TcModelMdf> findAllByModelKey(
            @Param("modelKey") long modelKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByMdfKey(@Param("mdfKey") long mdfKey);
}
