package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.common.ModelStatus;
import com.nori.tc.db.domain.common.ProtocolType;
import com.nori.tc.db.domain.model.TcModel;

/**
 * tc_model Mapper (FIX)
 *
 * - 이 모듈은 "보수적인 CRUD"만 제공한다.
 * - model_key 생성(IDENTITY) 처리 때문에 insert 후 key를 직접 반환하지 않는다.
 * → insert 후 findByNameVersion으로 재조회하는 방식(벤더 중립)을 권장한다.
 * - [2024-XX-XX FIX] 메모리 페이징 이슈 해결을 위해 findAll에 DB 페이징 파라미터 추가
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
     * 간단 검색 (FIX: DB 페이징 적용)
     * - modelNameLike: null이면 조건 미적용, 아니면 "%like%" 형태로 사용
     * - protocolType/status: null이면 조건 미적용
     */
    List<TcModel> findAll(
            @Param("modelNameLike") String modelNameLike,
            @Param("protocolType") ProtocolType protocolType,
            @Param("status") ModelStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByModelKey(@Param("modelKey") long modelKey);
}