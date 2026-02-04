package com.nori.tc.db.jpa.common.store;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpLogStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpLog;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
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
        validateCommand(command);

        try {
            final String eqpId = command.eqpId();

            final TcEqpLogEntity entity = repository.findById(eqpId)
                    .orElseGet(() -> TcEqpLogEntity.newEntity(eqpId));

            mapper.updateEntity(command, entity);

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
    public Optional<TcEqpLog> findByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            return repository.findById(eqpId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_log] findByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            repository.deleteById(eqpId);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_log] deleteByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    private void validateCommand(UpsertTcEqpLog command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpId() == null || command.eqpId().isBlank()) throw new IllegalArgumentException("command.eqpId must not be null/blank");
        if (command.logLevel() == null) throw new IllegalArgumentException("command.logLevel must not be null");
        if (command.logPath() == null || command.logPath().isBlank()) throw new IllegalArgumentException("command.logPath must not be null/blank");
    }
}