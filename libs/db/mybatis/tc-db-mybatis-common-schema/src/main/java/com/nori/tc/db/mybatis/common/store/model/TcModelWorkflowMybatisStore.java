package com.nori.tc.db.mybatis.common.store.model;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelWorkflowStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelWorkflow;
import com.nori.tc.db.domain.model.TcModelWorkflow;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelWorkflowMapper;

/**
 * tc_model_workflow MyBatis Store 구현체.
 *
 * upsert 주의
 * - common-schema의 TcModelWorkflowMapper.xml은 벤더 중립성을 위해 "generated key 반환"을 하지 않는다.
 * - 따라서 insert 후 (model_key, workflow_name, message_name)으로 재조회하여 workflow_key를 확보한다.
 */
@Repository
public class TcModelWorkflowMybatisStore implements TcModelWorkflowStore {

    private final TcModelWorkflowMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcModelWorkflowMybatisStore(TcModelWorkflowMapper mapper) {
        this.mapper = mapper;
    }

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    @Override
    @Transactional
    public TcModelWorkflow upsert(UpsertTcModelWorkflow command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        final Long workflowKey = command.workflowKey();
        final long modelKey = command.modelKey();
        final String workflowName = command.workflowName();
        final String messageName = command.messageName();

        final long resolvedKey = resolveKey(workflowKey, modelKey, workflowName, messageName);

        final TcModelWorkflow row = new TcModelWorkflow(
                resolvedKey,
                modelKey,
                workflowName,
                messageName,
                command.eventId(),
                command.transactionId(),
                command.workflowFilter(),
                command.actionName(),
                command.actionDataIndex(),
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_model_workflow insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByModelKeyAndWorkflowNameAndMessageName(modelKey, workflowName, messageName)
                    .orElseThrow(() -> new DbAccessException(
                            "tc_model_workflow upsert succeeded but row not found. key=" + modelKey + "/" + workflowName + "/" + messageName
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_model_workflow upsert duplicate (model_key, workflow_name, message_name). key=" + modelKey + "/" + workflowName + "/" + messageName,
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_model_workflow upsert failed. key=" + modelKey + "/" + workflowName + "/" + messageName,
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_model_workflow upsert failed (unexpected). key=" + modelKey + "/" + workflowName + "/" + messageName,
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workflowKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelWorkflow> findByWorkflowKey(long workflowKey) {
        try {
            return mapper.findByWorkflowKey(workflowKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_workflow findByWorkflowKey failed. workflowKey=" + workflowKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_workflow findByWorkflowKey failed (unexpected). workflowKey=" + workflowKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param workflowName DB MyBatis 계층 처리에 사용하는 입력 값
     * @param messageName 처리할 원본 데이터
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelWorkflow> findByModelKeyAndWorkflowNameAndMessageName(
            long modelKey,
            String workflowName,
            String messageName
    ) {
        try {
            return mapper.findByModelKeyAndWorkflowNameAndMessageName(modelKey, workflowName, messageName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_workflow findByUnique failed. key=" + modelKey + "/" + workflowName + "/" + messageName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_workflow findByUnique failed (unexpected). key=" + modelKey + "/" + workflowName + "/" + messageName, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelWorkflow> findAllByModelKey(long modelKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByModelKey(
                    modelKey,
                    p.offset(),
                    p.limit()
            );
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_workflow findAllByModelKey failed.", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_workflow findAllByModelKey failed (unexpected).", e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workflowKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByWorkflowKey(long workflowKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        try {
            // 삭제는 멱등으로 둔다: 없어도 예외를 던지지 않는다.
            mapper.deleteByWorkflowKey(workflowKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_workflow deleteByWorkflowKey failed. workflowKey=" + workflowKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_workflow deleteByWorkflowKey failed (unexpected). workflowKey=" + workflowKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workflowKey 대상 키 값
     * @param modelKey 대상 키 값
     * @param workflowName DB MyBatis 계층 처리에 사용하는 입력 값
     * @param messageName 처리할 원본 데이터
     * @return DB MyBatis 계층 처리 결과
     */
    private long resolveKey(Long workflowKey, long modelKey, String workflowName, String messageName) {
        if (workflowKey != null) {
            if (workflowKey <= 0) {
                throw new IllegalArgumentException("workflowKey must be > 0 when provided");
            }
            return workflowKey;
        }

        return mapper.findByModelKeyAndWorkflowNameAndMessageName(modelKey, workflowName, messageName)
                .map(TcModelWorkflow::workflowKey)
                .orElse(0L);
    }
}