package com.nori.tc.db.jpa.common.store.eqp;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStateHistStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpStateHist;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpStateHist;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpStateHistEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpStateHistEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpStateHistJpaRepository;

/**
 * tc_eqp_state_hist JPA Store 구현체.
 */
@Repository
public class TcEqpStateHistJpaStore implements TcEqpStateHistStore {

    private final TcEqpStateHistJpaRepository repository;
    private final TcEqpStateHistEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcEqpStateHistJpaStore(TcEqpStateHistJpaRepository repository, TcEqpStateHistEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void append(UpsertTcEqpStateHist command) {
        UpsertTcEqpStateHist normalized = normalizeCommand(command);
        validateCommand(normalized);

        try {
            TcEqpStateHistEntity entity = TcEqpStateHistEntity.newEntity(normalized.eqpKey(), normalized.stateType());
            mapper.updateEntity(normalized, entity);
            repository.save(entity);
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_state_hist] append failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state_hist] append failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpStateHist> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcEqpStateHistEntity> cq = cb.createQuery(TcEqpStateHistEntity.class);
            Root<TcEqpStateHistEntity> root = cq.from(TcEqpStateHistEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("eqpKey"), eqpKey))
                    .orderBy(cb.desc(root.get("changedAt")), cb.desc(root.get("stateHistKey")));

            TypedQuery<TcEqpStateHistEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state_hist] findAllByEqpKey failed", e);
        }
    }

    private void validateCommand(UpsertTcEqpStateHist command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() == null || command.eqpKey() <= 0) {
            throw new IllegalArgumentException("command.eqpKey must be positive");
        }
        if (command.stateType() == null) {
            throw new IllegalArgumentException("command.stateType must not be null");
        }
    }

    private UpsertTcEqpStateHist normalizeCommand(UpsertTcEqpStateHist command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcEqpStateHist(
                command.eqpKey(),
                command.stateType(),
                command.fromState(),
                command.toState(),
                command.changedAt() == null ? OffsetDateTime.now() : command.changedAt(),
                command.reasonCode(),
                command.reasonDetail()
        );
    }
}
