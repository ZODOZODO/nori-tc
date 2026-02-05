package com.nori.tc.db.jpa.common.store.model;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelEventIdStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelEventId;
import com.nori.tc.db.domain.model.TcModelEventId;
import com.nori.tc.db.jpa.common.entity.model.TcModelEventIdEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelEventIdEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelEventIdJpaRepository;

/**
 * tc_model_eventid JPA Store 구현체.
 */
@Repository
public class TcModelEventIdJpaStore implements TcModelEventIdStore {

    private final TcModelEventIdJpaRepository repository;
    private final TcModelEventIdEntityMapper mapper;

    public TcModelEventIdJpaStore(TcModelEventIdJpaRepository repository, TcModelEventIdEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelEventId upsert(UpsertTcModelEventId command) {
        UpsertTcModelEventId normalized = normalizeCommand(command);
        validateCommand(normalized);

        try {
            final Long modelKey = normalized.modelKey();
            final String eventId = normalized.eventId();

            final TcModelEventIdEntity entity = repository.findByModelKeyAndEventId(modelKey, eventId)
                    .orElseGet(() -> TcModelEventIdEntity.newEntity(modelKey, eventId));

            mapper.updateEntity(normalized, entity);

            TcModelEventIdEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_eventid] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_eventid] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelEventId> findByEventKey(long eventKey) {
        if (eventKey <= 0) {
            throw new IllegalArgumentException("eventKey must be positive");
        }
        try {
            return repository.findById(eventKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_eventid] findByEventKey failed: eventKey=" + eventKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelEventId> findByModelKeyAndEventId(long modelKey, String eventId) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be positive");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be null/blank");
        }
        try {
            return repository.findByModelKeyAndEventId(modelKey, eventId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_eventid] findByModelKeyAndEventId failed: modelKey=" + modelKey + ", eventId=" + eventId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEventKey(long eventKey) {
        if (eventKey <= 0) {
            throw new IllegalArgumentException("eventKey must be positive");
        }
        try {
            repository.deleteById(eventKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_eventid] deleteByEventKey failed: eventKey=" + eventKey, e);
        }
    }

    private void validateCommand(UpsertTcModelEventId command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.modelKey() == null || command.modelKey() <= 0) {
            throw new IllegalArgumentException("command.modelKey must be positive");
        }
        if (command.eventId() == null || command.eventId().isBlank()) {
            throw new IllegalArgumentException("command.eventId must not be null/blank");
        }
        if (command.enabled() == null) {
            throw new IllegalArgumentException("command.enabled must not be null");
        }
    }

    private UpsertTcModelEventId normalizeCommand(UpsertTcModelEventId command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcModelEventId(
                command.modelKey(),
                command.eventId(),
                command.reportId(),
                command.enabled() == null ? Boolean.FALSE : command.enabled()
        );
    }
}
