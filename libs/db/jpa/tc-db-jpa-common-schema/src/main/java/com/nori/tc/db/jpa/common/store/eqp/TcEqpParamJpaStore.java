package com.nori.tc.db.jpa.common.store.eqp;

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
import com.nori.tc.db.core.eqp.store.TcEqpParamStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParam;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpParam;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpParamEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpParamEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpParamJpaRepository;

/**
 * tc_eqp_param JPA Store 구현체.
 */
@Repository
public class TcEqpParamJpaStore implements TcEqpParamStore {

    private final TcEqpParamJpaRepository repository;
    private final TcEqpParamEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcEqpParamJpaStore(TcEqpParamJpaRepository repository, TcEqpParamEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpParam upsert(UpsertTcEqpParam command) {
        validateUpsert(command);

        try {
            Optional<TcEqpParamEntity> existing = repository.findByEqpKeyAndParamNameAndParamVersion(
                    command.eqpKey(),
                    command.paramName(),
                    command.paramVersion()
            );

            TcEqpParamEntity entity = existing.orElseGet(
                    () -> TcEqpParamEntity.newEntity(command.eqpKey(), command.paramName(), command.paramVersion())
            );

            mapper.updateEntity(command, entity);

            TcEqpParamEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_param] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_param] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpParam> findByEqpKeyAndNameVersion(long eqpKey, String paramName, String paramVersion) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName must not be null/blank");
        }
        if (paramVersion == null || paramVersion.isBlank()) {
            throw new IllegalArgumentException("paramVersion must not be null/blank");
        }

        try {
            return repository.findByEqpKeyAndParamNameAndParamVersion(eqpKey, paramName, paramVersion)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_param] findByEqpKeyAndNameVersion failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpParam> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcEqpParamEntity> cq = cb.createQuery(TcEqpParamEntity.class);
            Root<TcEqpParamEntity> root = cq.from(TcEqpParamEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("eqpKey"), eqpKey))
                    .orderBy(cb.asc(root.get("eqpParamKey")));

            TypedQuery<TcEqpParamEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_param] findAllByEqpKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpParamKey(long eqpParamKey) {
        if (eqpParamKey <= 0) {
            throw new IllegalArgumentException("eqpParamKey must be > 0");
        }
        try {
            repository.deleteById(eqpParamKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_param] deleteByEqpParamKey failed: eqpParamKey=" + eqpParamKey, e);
        }
    }

    private void validateUpsert(UpsertTcEqpParam command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be > 0");
        if (command.paramName() == null || command.paramName().isBlank()) throw new IllegalArgumentException("command.paramName must not be null/blank");
        if (command.paramVersion() == null || command.paramVersion().isBlank()) throw new IllegalArgumentException("command.paramVersion must not be null/blank");
    }
}
