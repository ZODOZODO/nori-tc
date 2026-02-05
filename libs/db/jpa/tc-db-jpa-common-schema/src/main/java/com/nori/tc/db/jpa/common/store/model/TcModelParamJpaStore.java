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
import com.nori.tc.db.core.model.store.TcModelParamStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelParam;
import com.nori.tc.db.domain.model.TcModelParam;
import com.nori.tc.db.jpa.common.entity.model.TcModelParamEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelParamEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelParamJpaRepository;

/**
 * tc_model_param JPA Store 구현체.
 */
@Repository
public class TcModelParamJpaStore implements TcModelParamStore {

    private final TcModelParamJpaRepository repository;
    private final TcModelParamEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcModelParamJpaStore(TcModelParamJpaRepository repository, TcModelParamEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelParam upsert(UpsertTcModelParam command) {
        validateUpsert(command);

        try {
            Optional<TcModelParamEntity> existing = repository.findByModelKeyAndParamName(
                    command.modelKey(),
                    command.paramName()
            );

            TcModelParamEntity entity = existing.orElseGet(
                    () -> TcModelParamEntity.newEntity(command.modelKey(), command.paramName())
            );

            mapper.updateEntity(command, entity);

            TcModelParamEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_param] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_param] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelParam> findByModelKeyAndName(long modelKey, String paramName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName must not be null/blank");
        }

        try {
            return repository.findByModelKeyAndParamName(modelKey, paramName)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_param] findByModelKeyAndName failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelParam> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelParamEntity> cq = cb.createQuery(TcModelParamEntity.class);
            Root<TcModelParamEntity> root = cq.from(TcModelParamEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("modelKey"), modelKey))
                    .orderBy(cb.asc(root.get("modelParamKey")));

            TypedQuery<TcModelParamEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_param] findAllByModelKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByModelParamKey(long modelParamKey) {
        if (modelParamKey <= 0) {
            throw new IllegalArgumentException("modelParamKey must be > 0");
        }
        try {
            repository.deleteById(modelParamKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_param] deleteByModelParamKey failed: modelParamKey=" + modelParamKey, e);
        }
    }

    private void validateUpsert(UpsertTcModelParam command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.paramName() == null || command.paramName().isBlank()) throw new IllegalArgumentException("command.paramName must not be null/blank");
    }
}
