package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
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
import com.nori.tc.db.core.model.store.TcModelStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.jpa.common.entity.model.TcModelEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelJpaRepository;

@Repository
public class TcModelJpaStore implements TcModelStore {

    private final EntityManager em;
    private final TcModelJpaRepository repository;
    private final TcModelEntityMapper mapper;

    public TcModelJpaStore(EntityManager em, TcModelJpaRepository repository, TcModelEntityMapper mapper) {
        this.em = em;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModel upsert(UpsertTcModel command) {
        if (command == null) throw new IllegalArgumentException("UpsertTcModel must not be null");
        if (command.modelName() == null || command.modelName().isBlank()) {
            throw new IllegalArgumentException("modelName must not be null/blank");
        }
        if (command.modelVersion() == null || command.modelVersion().isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be null/blank");
        }

        try {
            TcModelEntity entity = resolveEntity(command);
            mapper.updateFromUpsert(command, entity);
            if (entity.getModelKey() == null && command.createdBy() != null && !command.createdBy().isBlank()) {
                entity.setCreatedBy(command.createdBy());
            }
            TcModelEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByModelKey(long modelKey) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be positive");
        }
        try {
            return repository.findById(modelKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] findByModelKey failed: modelKey=" + modelKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByNameVersion(String modelName, String modelVersion) {
        if (modelName == null || modelName.isBlank()) throw new IllegalArgumentException("modelName must not be null/blank");
        if (modelVersion == null || modelVersion.isBlank()) throw new IllegalArgumentException("modelVersion must not be null/blank");

        try {
            return repository.findByModelNameAndModelVersion(modelName, modelVersion).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] findByNameVersion failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModel> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelEntity> cq = cb.createQuery(TcModelEntity.class);
            Root<TcModelEntity> root = cq.from(TcModelEntity.class);

            cq.select(root);
            cq.orderBy(cb.desc(root.get("updatedAt")), cb.asc(root.get("modelName")));

            TypedQuery<TcModelEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] findAll failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByModelKey(long modelKey) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be positive");
        }
        try {
            repository.deleteById(modelKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] deleteByModelKey failed", e);
        }
    }

    private TcModelEntity resolveEntity(UpsertTcModel command) {
        Long modelKey = command.modelKey();
        if (modelKey != null && modelKey > 0) {
            return repository.findById(modelKey)
                    .orElseGet(() -> TcModelEntity.newEntity(command.modelName(), command.modelVersion()));
        }
        return repository.findByModelNameAndModelVersion(command.modelName(), command.modelVersion())
                .orElseGet(() -> TcModelEntity.newEntity(command.modelName(), command.modelVersion()));
    }
}
