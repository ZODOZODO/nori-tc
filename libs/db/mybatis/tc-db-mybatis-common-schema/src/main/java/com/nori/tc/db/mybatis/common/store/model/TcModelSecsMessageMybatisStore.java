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
import com.nori.tc.db.core.model.store.TcModelSecsMessageStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelSecsMessageMapper;

/**
 * tc_model_secs_message MyBatis Store 구현체.
 *
 * upsert 주의
 * - common-schema의 TcModelSecsMessageMapper.xml은 벤더 중립성을 위해 "generated key 반환"을 하지 않는다.
 * - 따라서 insert 후 (model_key, secs_msg_name)으로 재조회하여 secs_msg_key를 확보한다.
 */
@Repository
public class TcModelSecsMessageMybatisStore implements TcModelSecsMessageStore {

    private final TcModelSecsMessageMapper mapper;

    public TcModelSecsMessageMybatisStore(TcModelSecsMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelSecsMessage upsert(UpsertTcModelSecsMessage command) {
        validateUpsert(command);

        final Long secsMsgKey = command.secsMsgKey();
        final long modelKey = command.modelKey();
        final String secsMsgName = command.secsMsgName();

        final long resolvedKey = resolveKey(secsMsgKey, modelKey, secsMsgName);

        final TcModelSecsMessage row = new TcModelSecsMessage(
                resolvedKey,
                modelKey,
                secsMsgName,
                command.description(),
                command.dataIndex(),
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_model_secs_message insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByModelKeyAndName(modelKey, secsMsgName)
                    .orElseThrow(() -> new DbAccessException("tc_model_secs_message upsert succeeded but row not found. modelKey=" + modelKey + ", secsMsgName=" + secsMsgName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_secs_message upsert duplicate (model_key, secs_msg_name). modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message upsert failed. modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message upsert failed (unexpected). modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSecsMessage> findBySecsMsgKey(long secsMsgKey) {
        try {
            return mapper.findBySecsMsgKey(secsMsgKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message findBySecsMsgKey failed. secsMsgKey=" + secsMsgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message findBySecsMsgKey failed (unexpected). secsMsgKey=" + secsMsgKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSecsMessage> findByModelKeyAndName(long modelKey, String secsMsgName) {
        try {
            return mapper.findByModelKeyAndName(modelKey, secsMsgName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message findByModelKeyAndName failed. modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message findByModelKeyAndName failed (unexpected). modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelSecsMessage> findAllByModelKey(long modelKey, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;
        try {
            return mapper.findAllByModelKey(modelKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message findAllByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message findAllByModelKey failed (unexpected). modelKey=" + modelKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteBySecsMsgKey(long secsMsgKey) {
        try {
            mapper.deleteBySecsMsgKey(secsMsgKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message deleteBySecsMsgKey failed. secsMsgKey=" + secsMsgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message deleteBySecsMsgKey failed (unexpected). secsMsgKey=" + secsMsgKey, e);
        }
    }

    private void validateUpsert(UpsertTcModelSecsMessage command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.secsMsgKey() != null && command.secsMsgKey() <= 0) {
            throw new IllegalArgumentException("command.secsMsgKey must be > 0 when provided");
        }
        if (command.modelKey() <= 0) {
            throw new IllegalArgumentException("command.modelKey must be > 0");
        }
        if (command.secsMsgName() == null || command.secsMsgName().isBlank()) {
            throw new IllegalArgumentException("command.secsMsgName must not be null/blank");
        }
    }

    private long resolveKey(Long secsMsgKey, long modelKey, String secsMsgName) {
        if (secsMsgKey != null) {
            if (secsMsgKey <= 0) {
                throw new IllegalArgumentException("secsMsgKey must be > 0 when provided");
            }
            return secsMsgKey;
        }

        return mapper.findByModelKeyAndName(modelKey, secsMsgName)
                .map(TcModelSecsMessage::secsMsgKey)
                .orElse(0L);
    }
}