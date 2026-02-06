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
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.work.store.TcWorkStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWork;
import com.nori.tc.db.domain.work.TcWork;
import com.nori.tc.db.jpa.common.entity.work.TcWorkEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkJpaRepository;

/**
 * tc_work JPA Store 구현체.
 */
@Repository
public class TcWorkJpaStore implements TcWorkStore {

    private final TcWorkJpaRepository repository;
    private final TcWorkEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcWorkJpaStore(TcWorkJpaRepository repository, TcWorkEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWork upsert(UpsertTcWork command) {
        validateUpsert(command);

        try {
            TcWorkEntity entity = resolveEntity(command);
            mapper.updateFromUpsert(command, entity);

            TcWorkEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWork> findByWorkKey(long workKey) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        try {
            return repository.findById(workKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] findByWorkKey failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWork> findByEqpKeyAndWorkId(long eqpKey, String workId) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (workId == null || workId.isBlank()) {
            throw new IllegalArgumentException("workId must not be null/blank");
        }

        try {
            return repository.findByEqpKeyAndWorkId(eqpKey, workId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] findByEqpKeyAndWorkId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWork> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkEntity> cq = cb.createQuery(TcWorkEntity.class);
            Root<TcWorkEntity> root = cq.from(TcWorkEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("eqpKey"), eqpKey))
                    .orderBy(cb.desc(root.get("workKey")));

            TypedQuery<TcWorkEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] findAllByEqpKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkKey(long workKey) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        try {
            repository.deleteById(workKey);
        } catch (EmptyResultDataAccessException ignore) {
            // 멱등 삭제: 존재하지 않아도 오류로 취급하지 않는다.
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] deleteByWorkKey failed: workKey=" + workKey, e);
        }
    }

    private void validateUpsert(UpsertTcWork command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be > 0");
        if (command.workId() == null || command.workId().isBlank()) {
            throw new IllegalArgumentException("command.workId must not be null/blank");
        }
        if (command.workState() == null) {
            throw new IllegalArgumentException("command.workState must not be null");
        }
        if (command.stepSeq() != null && command.stepSeq() < 0) {
            throw new IllegalArgumentException("command.stepSeq must be >= 0 when provided");
        }
    }

    private TcWorkEntity resolveEntity(UpsertTcWork command) {
        if (command.workKey() != null) {
            if (command.workKey() <= 0) {
                throw new IllegalArgumentException("workKey must be > 0 when provided");
            }
            return repository.findById(command.workKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_work] not found: workKey=" + command.workKey()
                    ));
        }

        return repository.findByEqpKeyAndWorkId(command.eqpKey(), command.workId())
                .orElseGet(() -> TcWorkEntity.newEntity(command.eqpKey(), command.workId()));
    }
}
