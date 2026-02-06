package com.nori.tc.db.mybatis.common.mapper.model;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.model.TcModelWorkflow;

/**
 * tc_model_workflow Mapper (FIX)
 *
 * - workflow_key 생성(IDENTITY) 처리 때문에 insert 후 key를 직접 반환하지 않는다.
 * - 벤더 중립성을 위해 insert 후 유니크 키로 재조회하는 방식을 권장한다.
 */
public interface TcModelWorkflowMapper {

    int insert(@Param("w") TcModelWorkflow workflow);

    int update(@Param("w") TcModelWorkflow workflow);

    Optional<TcModelWorkflow> findByWorkflowKey(@Param("workflowKey") long workflowKey);

    Optional<TcModelWorkflow> findByModelKeyAndWorkflowNameAndMessageName(
            @Param("modelKey") long modelKey,
            @Param("workflowName") String workflowName,
            @Param("messageName") String messageName
    );

    /**
     * 특정 모델(model_key)의 워크플로 목록 조회.
     * - DB 페이징 적용
     */
    List<TcModelWorkflow> findAllByModelKey(
            @Param("modelKey") long modelKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByWorkflowKey(@Param("workflowKey") long workflowKey);
}