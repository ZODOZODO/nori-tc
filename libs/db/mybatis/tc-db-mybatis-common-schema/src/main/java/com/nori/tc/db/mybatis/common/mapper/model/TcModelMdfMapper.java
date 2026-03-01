package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelMdf;

/**
 * {@code tc_model_mdf} MyBatis Mapper입니다.
 */
public interface TcModelMdfMapper {

    /**
     * MDF를 삽입합니다.
     */
    int insert(@Param("m") TcModelMdf modelMdf);

    /**
     * MDF를 갱신합니다.
     */
    int update(@Param("m") TcModelMdf modelMdf);

    /**
     * {@code mdf_key} 기준 단건 조회입니다.
     */
    Optional<TcModelMdf> findByMdfKey(@Param("mdfKey") long mdfKey);

    /**
     * {@code model_version_key} 기준 단건 조회입니다.
     */
    Optional<TcModelMdf> findByModelVersionKey(@Param("modelVersionKey") long modelVersionKey);

    /**
     * {@code model_version_key} 기준 목록 조회입니다.
     */
    List<TcModelMdf> findAllByModelVersionKey(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * {@code mdf_key} 기준 삭제입니다.
     */
    int deleteByMdfKey(@Param("mdfKey") long mdfKey);
}
