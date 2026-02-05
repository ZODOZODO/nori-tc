package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.TcModelVariableIdStore;
import com.nori.tc.db.core.model.UpsertTcModelVariableId;
import com.nori.tc.db.domain.common.VariableIdType;
import com.nori.tc.db.domain.model.TcModelVariableId;
import com.nori.tc.db.jpa.common.entity.model.TcModelVariableIdEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelVariableIdEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelVariableIdJpaRepository;

/**
 * tc_model_variableid JPA Store 구현체.
 */
@Repository
public class TcModelVariableIdJpaStore implements TcModelVariableIdStore {

    private final TcModelVariableIdJpaRepository repository;
    private final TcModelVariableIdEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcModelVariableIdJpaStore(
            TcModelVariableIdJpaRepository repository,
            TcModelVariableIdEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelVariableId upsert(UpsertTcModelVariableId command) {
        validateUpsert(command);

        try {
            Optional<TcModelVariableIdEntity> existing = repository.findByModelKeyAndVariableIdTypeAndVariableId(
                    command.modelKey(),
                    command.variableIdType(),
                    command.variableId()
            );

            TcModelVariableIdEntity entity = existing.orElseGet(
                    () -> TcModelVariableIdEntity.newEntity(
                            command.modelKey(),
                            command.variableIdType(),
                            command.variableId()
                    )
            );

            mapper.updateEntity(command, entity);

            TcModelVariableIdEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_variableid] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_variableid] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelVariableId> findByVariableKey(long variableKey) {
        if (variableKey <= 0) {
            throw new IllegalArgumentException("variableKey must be > 0");
        }

        try {
            return repository.findById(variableKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_variableid] findByVariableKey failed: variableKey=" + variableKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelVariableId> findByModelKeyAndTypeAndVariableId(
            long modelKey,
            VariableIdType variableIdType,
            String variableId
    ) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (variableIdType == null) {
            throw new IllegalArgumentException("variableIdType must not be null");
        }
        if (variableId == null || variableId.isBlank()) {
            throw new IllegalArgumentException("variableId must not be null/blank");
        }

        try {
            return repository.findByModelKeyAndVariableIdTypeAndVariableId(modelKey, variableIdType, variableId)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_variableid] findByModelKeyAndTypeAndVariableId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelVariableId> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelVariableIdEntity> cq = cb.createQuery(TcModelVariableIdEntity.class);
            Root<TcModelVariableIdEntity> root = cq.from(TcModelVariableIdEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("modelKey"), modelKey))
                    .orderBy(cb.asc(root.get("variableKey")));

            TypedQuery<TcModelVariableIdEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_variableid] findAllByModelKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByVariableKey(long variableKey) {
        if (variableKey <= 0) {
            throw new IllegalArgumentException("variableKey must be > 0");
        }
        try {
            repository.deleteById(variableKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_variableid] deleteByVariableKey failed: variableKey=" + variableKey, e);
        }
    }

    private void validateUpsert(UpsertTcModelVariableId command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.variableIdType() == null) throw new IllegalArgumentException("command.variableIdType must not be null");
        if (command.variableId() == null || command.variableId().isBlank()) throw new IllegalArgumentException("command.variableId must not be null/blank");
    }
}
