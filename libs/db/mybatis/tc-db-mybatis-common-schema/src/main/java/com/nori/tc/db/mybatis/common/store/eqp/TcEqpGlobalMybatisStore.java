package com.nori.tc.db.mybatis.common.store.eqp;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpGlobalStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpGlobal;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpGlobal;
import com.nori.tc.db.mybatis.common.mapper.eqp.TcEqpGlobalMapper;

/**
 * tc_eqp_global MyBatis Store 구현체.
 *
 * - 유니크 키(eqp_key, param_name) 기반 upsert 제공
 */
@Repository
public class TcEqpGlobalMybatisStore implements TcEqpGlobalStore {

    private final TcEqpGlobalMapper mapper;

    public TcEqpGlobalMybatisStore(TcEqpGlobalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpGlobal upsert(UpsertTcEqpGlobal command) {
        validateCommand(command);

        final long eqpKey = command.eqpKey();
        final String paramName = command.paramName();

        final TcEqpGlobal row = new TcEqpGlobal(
                0L,
                eqpKey,
                paramName,
                command.paramValue(),
                command.updatedAt()
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.update(row);
                }
            }

            return mapper.findByEqpKeyAndParamName(eqpKey, paramName)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_global upsert succeeded but row not found. eqpKey=" + eqpKey + ", paramName=" + paramName));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_global upsert duplicate key. eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_global upsert failed. eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_global upsert failed (unexpected). eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpGlobal> findByEqpKeyAndParamName(long eqpKey, String paramName) {
        try {
            return mapper.findByEqpKeyAndParamName(eqpKey, paramName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_global findByEqpKeyAndParamName failed. eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_global findByEqpKeyAndParamName failed (unexpected). eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpGlobal> findByEqpKey(long eqpKey) {
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_global findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_global findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKeyAndParamName(long eqpKey, String paramName) {
        try {
            mapper.deleteByEqpKeyAndParamName(eqpKey, paramName);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_global deleteByEqpKeyAndParamName failed. eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_global deleteByEqpKeyAndParamName failed (unexpected). eqpKey=" + eqpKey + ", paramName=" + paramName, e);
        }
    }

    private void validateCommand(UpsertTcEqpGlobal command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be positive");
        if (command.paramName() == null || command.paramName().isBlank()) throw new IllegalArgumentException("command.paramName must not be null/blank");
    }
}
