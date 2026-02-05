package com.nori.tc.db.mybatis.common.store.model;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.TcModelEventIdStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelEventId;
import com.nori.tc.db.domain.model.TcModelEventId;
import com.nori.tc.db.mybatis.common.mapper.model.TcModelEventIdMapper;

/**
 * tc_model_eventid MyBatis Store 구현체.
 */
@Repository
public class TcModelEventIdMybatisStore implements TcModelEventIdStore {

    private final TcModelEventIdMapper mapper;

    public TcModelEventIdMybatisStore(TcModelEventIdMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelEventId upsert(UpsertTcModelEventId command) {
        UpsertTcModelEventId normalized = normalizeCommand(command);
        validateCommand(normalized);

        final long modelKey = normalized.modelKey();
        final String eventId = normalized.eventId();

        final TcModelEventId row = new TcModelEventId(
                0L,
                modelKey,
                eventId,
                normalized.reportId(),
                normalized.enabled(),
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

            return mapper.findByModelKeyAndEventId(modelKey, eventId)
                    .orElseThrow(() -> new DbAccessException("tc_model_eventid upsert succeeded but row not found. modelKey=" + modelKey + ", eventId=" + eventId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_model_eventid upsert duplicate key. modelKey=" + modelKey + ", eventId=" + eventId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_eventid upsert failed. modelKey=" + modelKey + ", eventId=" + eventId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_eventid upsert failed (unexpected). modelKey=" + modelKey + ", eventId=" + eventId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelEventId> findByEventKey(long eventKey) {
        try {
            return mapper.findByEventKey(eventKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_eventid findByEventKey failed. eventKey=" + eventKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_eventid findByEventKey failed (unexpected). eventKey=" + eventKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelEventId> findByModelKeyAndEventId(long modelKey, String eventId) {
        try {
            return mapper.findByModelKeyAndEventId(modelKey, eventId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_eventid findByModelKeyAndEventId failed. modelKey=" + modelKey + ", eventId=" + eventId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_eventid findByModelKeyAndEventId failed (unexpected). modelKey=" + modelKey + ", eventId=" + eventId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEventKey(long eventKey) {
        try {
            mapper.deleteByEventKey(eventKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_model_eventid deleteByEventKey failed. eventKey=" + eventKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_model_eventid deleteByEventKey failed (unexpected). eventKey=" + eventKey, e);
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
