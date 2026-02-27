package com.nori.tc.db.core.model.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.model.upsert.UpsertTcModelWorkflow;
import com.nori.tc.db.domain.model.TcModelWorkflow;

/**
 * tc_model_workflow CRUD 인터이스 (기술 중립)
 *
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 */
public interface TcModelWorkflowStore {

    /**
     * 워크플로 upsert.
     *
     * <p>
     * - workflow_key가 있으면 해당 PK 기반으로 갱신합니다.
     * - workflow_key가 없으면 (model_version_key, workflow_name, message_name) 유니크 키 기준으로
     * 존재 여부를 확인한 뒤 갱신/생성을 수행합니다.
     * </p>
     *
     * @return upsert 후 상태의 TcModelWorkflow
     */
    TcModelWorkflow upsert(UpsertTcModelWorkflow command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workflowKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelWorkflow> findByWorkflowKey(long workflowKey);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param workflowName DB Core 계층 처리에 사용하는 입력 값
     * @param messageName 처리할 원본 데이터
     * @return 조회 결과(Optional)
     */
    Optional<TcModelWorkflow> findByModelVersionKeyAndWorkflowNameAndMessageName(
            long modelVersionKey,
            String workflowName,
            String messageName
    );

    /**
     * 특정 모델(model_version_key)의 워크플로 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcModelWorkflow> findAllByModelVersionKey(long modelVersionKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workflowKey 대상 키 값
     */
    void deleteByWorkflowKey(long workflowKey);
}
