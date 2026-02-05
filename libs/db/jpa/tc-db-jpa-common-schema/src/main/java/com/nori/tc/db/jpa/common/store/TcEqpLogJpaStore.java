package com.nori.tc.db.jpa.common.store;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpLogStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpLog;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.common.LogLevel;
import com.nori.tc.db.domain.eqp.TcEqpLog;
import com.nori.tc.db.jpa.common.entity.TcEqpLogEntity;
import com.nori.tc.db.jpa.common.mapper.TcEqpLogEntityMapper;
import com.nori.tc.db.jpa.common.repository.TcEqpLogJpaRepository;

/**
 * tc_eqp_log JPA Store 구현체.
 */
@Repository
public class TcEqpLogJpaStore implements TcEqpLogStore {

    private final TcEqpLogJpaRepository repository;
    private final TcEqpLogEntityMapper mapper;

    public TcEqpLogJpaStore(TcEqpLogJpaRepository repository, TcEqpLogEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpLog upsert(UpsertTcEqpLog command) {
        UpsertTcEqpLog normalized = normalizeCommand(command);
        validateCommand(normalized);

        try {
            final Long eqpKey = normalized.eqpKey();

            final TcEqpLogEntity entity = repository.findById(eqpKey)
                    .orElseGet(() -> TcEqpLogEntity.newEntity(eqpKey));

            mapper.updateEntity(normalized, entity);

            TcEqpLogEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_log] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_log] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpLog> findByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_log] findByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be positive");
        }
        try {
            repository.deleteById(eqpKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_log] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    private void validateCommand(UpsertTcEqpLog command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() == null || command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be positive");
        if (command.logLevel() == null) throw new IllegalArgumentException("command.logLevel must not be null");
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
