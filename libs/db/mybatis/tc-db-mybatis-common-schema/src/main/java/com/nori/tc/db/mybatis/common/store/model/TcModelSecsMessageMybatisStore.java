package com.nori.tc.db.mybatis.common.store.model;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.model.NewTcModelSecsMessage;
import com.nori.tc.db.core.model.TcModelSecsMessageStore;
import com.nori.tc.db.core.model.UpdateTcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelSecsMessageMapper;

/**
 * tc_model_secs_message MyBatis Store 구현체.
 *
 * 생성(create) 주의
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
    public TcModelSecsMessage create(NewTcModelSecsMessage command) {
        validateCreate(command);

        final long modelKey = command.modelKey();
        final String secsMsgName = command.secsMsgName();

        final TcModelSecsMessage row = new TcModelSecsMessage(
                0L,
                modelKey,
                secsMsgName,
                command.description(),
                command.dataIndex(),
                null
        );

        try {
            int inserted = mapper.insert(row);
            if (inserted != 1) {
                throw new DbAccessException("tc_model_secs_message insert affected rows != 1. affected=" + inserted);
            }

            return mapper.findByModelKeyAndName(modelKey, secsMsgName)
                    .orElseThrow(() -> new DbAccessException("tc_model_secs_message insert succeeded but row not found. modelKey=" + modelKey + ", secsMsgName=" + secsMsgName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_secs_message duplicate (model_key, secs_msg_name). modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message create failed. modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message create failed (unexpected). modelKey=" + modelKey + ", secsMsgName=" + secsMsgName, e);
        }
    }

    @Override
    @Transactional
    public TcModelSecsMessage update(UpdateTcModelSecsMessage command) {
        validateUpdate(command);

        final long secsMsgKey = command.secsMsgKey();

        final TcModelSecsMessage row = new TcModelSecsMessage(
                secsMsgKey,
                command.modelKey(),
                command.secsMsgName(),
                command.description(),
                command.dataIndex(),
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                throw new DbEntityNotFoundException("tc_model_secs_message not found for update. secsMsgKey=" + secsMsgKey);
            }

            return mapper.findBySecsMsgKey(secsMsgKey)
                    .orElseThrow(() -> new DbAccessException("tc_model_secs_message update succeeded but row not found. secsMsgKey=" + secsMsgKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_secs_message update duplicate (model_key, secs_msg_name). secsMsgKey=" + secsMsgKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message update failed. secsMsgKey=" + secsMsgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message update failed (unexpected). secsMsgKey=" + secsMsgKey, e);
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
    public List<TcModelSecsMessage> findByModelKey(long modelKey) {
        try {
            return mapper.findByModelKey(modelKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_secs_message findByModelKey failed. modelKey=" + modelKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_secs_message findByModelKey failed (unexpected). modelKey=" + modelKey, e);
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

    private void validateCreate(NewTcModelSecsMessage command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.modelKey() <= 0) {
            throw new IllegalArgumentException("command.modelKey must be > 0");
        }
        if (command.secsMsgName() == null || command.secsMsgName().isBlank()) {
            throw new IllegalArgumentException("command.secsMsgName must not be null/blank");
        }
    }

    private void validateUpdate(UpdateTcModelSecsMessage command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.secsMsgKey() <= 0) {
            throw new IllegalArgumentException("command.secsMsgKey must be > 0");
        }
        if (command.modelKey() <= 0) {
            throw new IllegalArgumentException("command.modelKey must be > 0");
        }
        if (command.secsMsgName() == null || command.secsMsgName().isBlank()) {
            throw new IllegalArgumentException("command.secsMsgName must not be null/blank");
        }
    }
}
