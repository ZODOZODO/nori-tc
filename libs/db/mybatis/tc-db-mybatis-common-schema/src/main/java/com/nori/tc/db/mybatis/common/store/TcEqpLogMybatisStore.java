package com.nori.tc.db.mybatis.common.store;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpLogStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpLog;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.common.LogLevel;
import com.nori.tc.db.domain.eqp.TcEqpLog;
import com.nori.tc.db.mybatis.common.mapper.TcEqpLogMapper;

/**
 * tc_eqp_log MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_key)
 */
@Repository
public class TcEqpLogMybatisStore implements TcEqpLogStore {

    private final TcEqpLogMapper mapper;

    public TcEqpLogMybatisStore(TcEqpLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpLog upsert(UpsertTcEqpLog command) {
        UpsertTcEqpLog normalized = normalizeCommand(command);
        validateCommand(normalized);

        final Long eqpKey = normalized.eqpKey();

        final TcEqpLog row = new TcEqpLog(
                eqpKey,
                normalized.logLevel(),
                normalized.logRetentionDays(),
                normalized.logPath(),
                null
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

            return mapper.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_log upsert succeeded but row not found. eqpKey=" + eqpKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_log upsert duplicate key. eqpKey=" + eqpKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_log upsert failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_log upsert failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpLog> findByEqpKey(long eqpKey) {
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_log findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_log findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        try {
            mapper.deleteByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_log deleteByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_log deleteByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    private void validateCommand(UpsertTcEqpLog command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.eqpKey() == null || command.eqpKey() <= 0) {
            throw new IllegalArgumentException("command.eqpKey must be positive");
        }
        if (command.logLevel() == null) {
            throw new IllegalArgumentException("command.logLevel must not be null");
        }
        if (command.logRetentionDays() < 1) {
            throw new IllegalArgumentException("command.logRetentionDays must be >= 1");
        }
    }

    private UpsertTcEqpLog normalizeCommand(UpsertTcEqpLog command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcEqpLog(
                command.eqpKey(),
                command.logLevel() == null ? LogLevel.INFO : command.logLevel(),
                command.logRetentionDays() == null ? 30 : command.logRetentionDays(),
                command.logPath()
        );
    }
}
