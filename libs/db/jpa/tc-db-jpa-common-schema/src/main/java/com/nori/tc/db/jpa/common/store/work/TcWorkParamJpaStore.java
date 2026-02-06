package com.nori.tc.db.jpa.common.store.work;

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
import com.nori.tc.db.core.work.store.TcWorkParamStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkParam;
import com.nori.tc.db.domain.work.TcWorkParam;
import com.nori.tc.db.jpa.common.entity.work.TcWorkParamEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkParamEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkParamJpaRepository;

/**
 * tc_work_param JPA Store 구현체.
 */
@Repository
public class TcWorkParamJpaStore implements TcWorkParamStore {

    private final TcWorkParamJpaRepository repository;
    private final TcWorkParamEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcWorkParamJpaStore(TcWorkParamJpaRepository repository, TcWorkParamEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkParam upsert(UpsertTcWorkParam command) {
        validateUpsert(command);

        try {
            Optional<TcWorkParamEntity> existing = repository.findByWorkKeyAndParamName(
                    command.workKey(),
                    command.paramName()
            );

            TcWorkParamEntity entity = existing.orElseGet(
                    () -> TcWorkParamEntity.newEntity(command.workKey(), command.paramName())
            );

            mapper.updateEntity(command, entity);

            TcWorkParamEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_param] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_param] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkParam> findByWorkKeyAndName(long workKey, String paramName) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName must not be null/blank");
        }

        try {
            return repository.findByWorkKeyAndParamName(workKey, paramName)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_param] findByWorkKeyAndName failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkParam> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkParamEntity> cq = cb.createQuery(TcWorkParamEntity.class);
            Root<TcWorkParamEntity> root = cq.from(TcWorkParamEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("workKey"), workKey))
                    .orderBy(cb.asc(root.get("workParamKey")));

            TypedQuery<TcWorkParamEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_param] findAllByWorkKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkParamKey(long workParamKey) {
        if (workParamKey <= 0) {
            throw new IllegalArgumentException("workParamKey must be > 0");
        }
        try {
            repository.deleteById(workParamKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_param] deleteByWorkParamKey failed: workParamKey=" + workParamKey, e);
        }
    }

    private void validateUpsert(UpsertTcWorkParam command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workKey() <= 0) throw new IllegalArgumentException("command.workKey must be > 0");
        if (command.paramName() == null || command.paramName().isBlank()) throw new IllegalArgumentException("command.paramName must not be null/blank");
    }
}
