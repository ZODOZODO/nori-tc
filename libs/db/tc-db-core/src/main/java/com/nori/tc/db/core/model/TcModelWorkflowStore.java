package com.nori.tc.db.core.model;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.domain.model.TcModelWorkflow;

/**
 * tc_model_workflow CRUD 인터페이스 (기술 중립)
 *
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 */
public interface TcModelWorkflowStore {

    /**
     * 워크플로 생성.
     *
     * @return DB가 부여한 workflow_key 및 updated_at이 채워진 TcModelWorkflow
     */
    TcModelWorkflow create(NewTcModelWorkflow command);

    /**
     * 워크플로 갱신.
     *
     * @return 갱신 후 상태의 TcModelWorkflow
     */
    TcModelWorkflow update(UpdateTcModelWorkflow command);

    Optional<TcModelWorkflow> findByWorkflowKey(long workflowKey);

    Optional<TcModelWorkflow> findByModelKeyAndWorkflowNameAndMessageName(
            long modelKey,
            String workflowName,
            String messageName
    );

    /**
     * 조건 검색 + 페이징
     */
    List<TcModelWorkflow> findAll(TcModelWorkflowSearchCriteria criteria, PageRequest page);

    void deleteByWorkflowKey(long workflowKey);
}
