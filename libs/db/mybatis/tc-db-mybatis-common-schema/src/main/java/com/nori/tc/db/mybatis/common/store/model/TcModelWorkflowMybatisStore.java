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

    public TcModelWorkflowMybatisStore(TcModelWorkflowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelWorkflow upsert(UpsertTcModelWorkflow command) {
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

    @Override
    @Transactional
    public void deleteByWorkflowKey(long workflowKey) {
        try {
            // 삭제는 멱등으로 둔다: 없어도 예외를 던지지 않는다.
            mapper.deleteByWorkflowKey(workflowKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_workflow deleteByWorkflowKey failed. workflowKey=" + workflowKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_workflow deleteByWorkflowKey failed (unexpected). workflowKey=" + workflowKey, e);
        }
    }

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