package com.nori.tc.db.jpa.common.store.eqp;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpStateStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpState;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpState;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpStateEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpStateEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpStateJpaRepository;

/**
 * tc_eqp_state JPA Store 구현체.
 */
@Repository
public class TcEqpStateJpaStore implements TcEqpStateStore {

    private final TcEqpStateJpaRepository repository;
    private final TcEqpStateEntityMapper mapper;

    public TcEqpStateJpaStore(TcEqpStateJpaRepository repository, TcEqpStateEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpState upsert(UpsertTcEqpState command) {
        validateCommand(command);

        try {
            final long eqpKey = command.eqpKey();

            final TcEqpStateEntity entity = repository.findById(eqpKey)
                    .orElseGet(() -> TcEqpStateEntity.newEntity(eqpKey));

            mapper.updateEntity(command, entity);

            TcEqpStateEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_state] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpState> findByEqpKey(long eqpKey) {
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state] findByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        try {
            repository.deleteById(eqpKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    private void validateCommand(UpsertTcEqpState command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be positive");
    }
}
