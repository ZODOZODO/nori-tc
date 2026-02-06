package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

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

@Repository
public class TcModelJpaStore implements TcModelStore {

    private final EntityManager em;
    private final TcModelRepository repository;
    private final TcModelMapper mapper;

    public TcModelJpaStore(EntityManager em, TcModelRepository repository, TcModelMapper mapper) {
        this.em = em;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModel upsert(UpsertTcModel command) {
        if (command == null) throw new IllegalArgumentException("UpsertTcModel must not be null");

        try {
            TcModelEntity saved;
            if (command.modelKey() != null && command.modelKey() > 0) {
                saved = repository.save(mapper.toEntity(command));
            } else {
                Optional<TcModelEntity> existing = repository.findByModelNameAndModelVersion(command.modelName(), command.modelVersion());
                if (existing.isPresent()) {
                    saved = repository.save(mapper.toEntity(command.withModelKey(existing.get().getModelKey())));
                } else {
                    saved = repository.save(mapper.toEntity(command));
                }
            }
            return mapper.toDomain(saved);

        } catch (RuntimeException e) {
            throw new DbDuplicateKeyException("[tc_model] upsert failed: integrity violation", e);
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

}
