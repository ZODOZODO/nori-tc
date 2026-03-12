package com.nori.tc.db.jpa.common.store.eqp;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpParamVersionStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParamVersion;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpParamVersion;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpParamVersionEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpParamVersionEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpParamVersionJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * tc_eqp_param_version JPA Store 구현체입니다.
 */
@Repository
public class TcEqpParamVersionJpaStore implements TcEqpParamVersionStore {

    private final TcEqpParamVersionJpaRepository repository;
    private final TcEqpParamVersionEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcEqpParamVersionJpaStore(
            final TcEqpParamVersionJpaRepository repository,
            final TcEqpParamVersionEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpParamVersion upsert(final UpsertTcEqpParamVersion command) {
        validateUpsert(command);

        try {
            final Optional<TcEqpParamVersionEntity> existing =
                    repository.findByEqpKeyAndParamVersion(command.eqpKey(), command.paramVersion());

            final TcEqpParamVersionEntity entity = existing.orElseGet(
                    () -> TcEqpParamVersionEntity.newEntity(command.eqpKey(), command.paramVersion())
            );

            mapper.updateEntity(command, entity);

            final TcEqpParamVersionEntity saved = repository.save(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_param_version] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_param_version] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpParamVersion> findByEqpKeyAndParamVersion(final long eqpKey, final String paramVersion) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (paramVersion == null || paramVersion.isBlank()) {
            throw new IllegalArgumentException("paramVersion must not be null/blank");
        }

        try {
            return repository.findByEqpKeyAndParamVersion(eqpKey, paramVersion)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_param_version] findByEqpKeyAndParamVersion failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpParamVersion> findAllByEqpKey(final long eqpKey, final PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest effectivePage = (page == null) ? PageRequest.defaultPage() : page;

        try {
            final CriteriaBuilder cb = em.getCriteriaBuilder();
            final CriteriaQuery<TcEqpParamVersionEntity> cq = cb.createQuery(TcEqpParamVersionEntity.class);
            final Root<TcEqpParamVersionEntity> root = cq.from(TcEqpParamVersionEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("eqpKey"), eqpKey))
                    .orderBy(cb.asc(root.get("eqpParamVersionKey")));

            final TypedQuery<TcEqpParamVersionEntity> query = em.createQuery(cq);
            query.setFirstResult(effectivePage.offset());
            query.setMaxResults(effectivePage.limit());

            return query.getResultList().stream()
                    .map(mapper::toDomain)
                    .toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_param_version] findAllByEqpKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteAllByEqpKey(final long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }

        try {
            repository.deleteAllByEqpKey(eqpKey);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_param_version] deleteAllByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    private void validateUpsert(final UpsertTcEqpParamVersion command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
    }
}
