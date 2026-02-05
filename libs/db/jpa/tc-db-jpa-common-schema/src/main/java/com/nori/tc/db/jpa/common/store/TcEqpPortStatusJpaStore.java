package com.nori.tc.db.jpa.common.store;

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
import com.nori.tc.db.core.eqp.store.TcEqpPortStatusStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpPortStatus;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpPortStatus;
import com.nori.tc.db.jpa.common.entity.TcEqpPortStatusEntity;
import com.nori.tc.db.jpa.common.mapper.TcEqpPortStatusEntityMapper;
import com.nori.tc.db.jpa.common.repository.TcEqpPortStatusJpaRepository;

/**
 * tc_eqp_port_status JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> (eqp_key, port_id) 유니크 키로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcEqpPortStatusJpaStore implements TcEqpPortStatusStore {

    private final TcEqpPortStatusJpaRepository repository;
    private final TcEqpPortStatusEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcEqpPortStatusJpaStore(TcEqpPortStatusJpaRepository repository, TcEqpPortStatusEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpPortStatus upsert(UpsertTcEqpPortStatus command) {
        validateCommand(command);

        try {
            final long eqpKey = command.eqpKey();
            final String portId = command.portId();

            TcEqpPortStatusEntity entity = repository.findByEqpKeyAndPortId(eqpKey, portId)
                    .orElseGet(() -> TcEqpPortStatusEntity.newEntity(eqpKey, portId));

            mapper.updateEntity(command, entity);

            TcEqpPortStatusEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_port_status] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_port_status] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpPortStatus> findByEqpKeyPortId(long eqpKey, String portId) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (portId == null || portId.isBlank()) {
            throw new IllegalArgumentException("portId must not be null/blank");
        }
        try {
            return repository.findByEqpKeyAndPortId(eqpKey, portId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_port_status] findByEqpKeyPortId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpPortStatus> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcEqpPortStatusEntity> cq = cb.createQuery(TcEqpPortStatusEntity.class);
            Root<TcEqpPortStatusEntity> root = cq.from(TcEqpPortStatusEntity.class);

            Predicate predicate = cb.equal(root.get("eqpKey"), eqpKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("portId")));

            TypedQuery<TcEqpPortStatusEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_port_status] findAllByEqpKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKeyPortId(long eqpKey, String portId) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (portId == null || portId.isBlank()) {
            throw new IllegalArgumentException("portId must not be null/blank");
        }
        try {
            repository.findByEqpKeyAndPortId(eqpKey, portId).ifPresent(repository::delete);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_port_status] deleteByEqpKeyPortId failed", e);
        }
    }

    private void validateCommand(UpsertTcEqpPortStatus command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be > 0");
        if (command.portId() == null || command.portId().isBlank()) throw new IllegalArgumentException("command.portId must not be null/blank");
    }
}
