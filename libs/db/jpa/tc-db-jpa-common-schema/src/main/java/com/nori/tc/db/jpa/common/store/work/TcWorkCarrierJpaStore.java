package com.nori.tc.db.jpa.common.store.work;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.work.store.TcWorkCarrierStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrier;
import com.nori.tc.db.domain.work.TcWorkCarrier;
import com.nori.tc.db.jpa.common.entity.work.TcWorkCarrierEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkCarrierEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkCarrierJpaRepository;

/**
 * tc_work_carrier JPA Store 구현체.
 *
 * <p>
 * <b>계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> (work_key, carrier_id) 유니크 키로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcWorkCarrierJpaStore implements TcWorkCarrierStore {

    private final TcWorkCarrierJpaRepository repository;
    private final TcWorkCarrierEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcWorkCarrierJpaStore(TcWorkCarrierJpaRepository repository, TcWorkCarrierEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkCarrier upsert(UpsertTcWorkCarrier command) {
        validateCommand(command);

        try {
            final long workKey = command.workKey();
            final String carrierId = command.carrierId();

            TcWorkCarrierEntity entity = repository.findByWorkKeyAndCarrierId(workKey, carrierId)
                    .orElseGet(() -> TcWorkCarrierEntity.newEntity(workKey, carrierId));

            mapper.updateEntity(command, entity);

            TcWorkCarrierEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_carrier] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkCarrier> findByWorkKeyCarrierId(long workKey, String carrierId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (carrierId == null || carrierId.isBlank()) {
            throw new IllegalArgumentException("carrierId must not be null/blank");
        }
        try {
            return repository.findByWorkKeyAndCarrierId(workKey, carrierId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier] findByWorkKeyCarrierId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkCarrier> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkCarrierEntity> cq = cb.createQuery(TcWorkCarrierEntity.class);
            Root<TcWorkCarrierEntity> root = cq.from(TcWorkCarrierEntity.class);

            Predicate predicate = cb.equal(root.get("workKey"), workKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("carrierId")));

            TypedQuery<TcWorkCarrierEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier] findAllByWorkKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkKeyCarrierId(long workKey, String carrierId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (carrierId == null || carrierId.isBlank()) {
            throw new IllegalArgumentException("carrierId must not be null/blank");
        }
        try {
            repository.findByWorkKeyAndCarrierId(workKey, carrierId).ifPresent(repository::delete);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier] deleteByWorkKeyCarrierId failed", e);
        }
    }

    private void validateCommand(UpsertTcWorkCarrier command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workKey() <= 0) throw new IllegalArgumentException("command.workKey must be > 0");
        if (command.carrierId() == null || command.carrierId().isBlank()) throw new IllegalArgumentException("command.carrierId must not be null/blank");
    }
}
