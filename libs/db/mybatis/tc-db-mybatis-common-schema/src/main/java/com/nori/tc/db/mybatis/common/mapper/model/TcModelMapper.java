package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModel;

/**
 * tc_model Mapper (FIX)
 *
 * - 이 모듈은 "보수적인 CRUD"만 제공한다.
 * - model_key 생성(IDENTITY) 처리 때문에 insert 후 key를 직접 반환하지 않는다.
 * → insert 후 findByNameVersion으로 재조회하는 방식(벤더 중립)을 권장한다.
 * - [2024-XX-XX FIX] 메모리 페이징 이슈 해결을 위해 findAll에 DB 페징 파라미터 추가
 */
public interface TcModelMapper {

    int insert(@Param("m") TcModel model);

    int update(@Param("m") TcModel model);

    Optional<TcModel> findByModelKey(@Param("modelKey") long modelKey);

    Optional<TcModel> findByNameVersion(
            @Param("modelName") String modelName,
            @Param("modelVersion") String modelVersion
    );

    /**
     * 목록 조회 (FIX: DB 페이징 적용)
     */
    List<TcModel> findAll(
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByModelKey(@Param("modelKey") long modelKey);
}
