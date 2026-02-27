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

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workflow DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("w") TcModelWorkflow workflow);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workflow DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("w") TcModelWorkflow workflow);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workflowKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelWorkflow> findByWorkflowKey(@Param("workflowKey") long workflowKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param workflowName DB MyBatis 계층 처리에 사용하는 입력 값
     * @param messageName 처리할 원본 데이터
     * @return 조회 결과(Optional)
     */
    Optional<TcModelWorkflow> findByModelVersionKeyAndWorkflowNameAndMessageName(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("workflowName") String workflowName,
            @Param("messageName") String messageName
    );

    /**
     * 특정 모델(model_version_key)의 워크플로 목록 조회.
     * - DB 페이징 적용
     */
    List<TcModelWorkflow> findAllByModelVersionKey(
            @Param("modelVersionKey") long modelVersionKey,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workflowKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByWorkflowKey(@Param("workflowKey") long workflowKey);
}