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
import com.nori.tc.db.core.work.store.TcWorkLotStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkLot;
import com.nori.tc.db.domain.work.TcWorkLot;
import com.nori.tc.db.jpa.common.entity.work.TcWorkLotEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkLotEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkLotJpaRepository;

/**
 * tc_work_lot JPA Store 구현체.
 */
@Repository
public class TcWorkLotJpaStore implements TcWorkLotStore {

    private final TcWorkLotJpaRepository repository;
    private final TcWorkLotEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcWorkLotJpaStore(TcWorkLotJpaRepository repository, TcWorkLotEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkLot upsert(UpsertTcWorkLot command) {
        validateUpsert(command);

        try {
            Optional<TcWorkLotEntity> existing = repository.findByWorkKeyAndLotId(
                    command.workKey(),
                    command.lotId()
            );

            TcWorkLotEntity entity = existing.orElseGet(
                    () -> TcWorkLotEntity.newEntity(command.workKey(), command.lotId())
            );

            mapper.updateEntity(command, entity);

            TcWorkLotEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_lot] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_lot] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkLot> findByWorkKeyAndLotId(long workKey, String lotId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (lotId == null || lotId.isBlank()) {
            throw new IllegalArgumentException("lotId must not be null/blank");
        }

        try {
            return repository.findByWorkKeyAndLotId(workKey, lotId)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_lot] findByWorkKeyAndLotId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkLot> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkLotEntity> cq = cb.createQuery(TcWorkLotEntity.class);
            Root<TcWorkLotEntity> root = cq.from(TcWorkLotEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("workKey"), workKey))
                    .orderBy(cb.asc(root.get("workLotKey")));

            TypedQuery<TcWorkLotEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_lot] findAllByWorkKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkLotKey(long workLotKey) {
        if (workLotKey <= 0) {
            throw new IllegalArgumentException("workLotKey must be > 0");
        }
        try {
            repository.deleteById(workLotKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_lot] deleteByWorkLotKey failed: workLotKey=" + workLotKey, e);
        }
    }

    private void validateUpsert(UpsertTcWorkLot command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workKey() <= 0) throw new IllegalArgumentException("command.workKey must be > 0");
        if (command.lotId() == null || command.lotId().isBlank()) throw new IllegalArgumentException("command.lotId must not be null/blank");
    }
}
