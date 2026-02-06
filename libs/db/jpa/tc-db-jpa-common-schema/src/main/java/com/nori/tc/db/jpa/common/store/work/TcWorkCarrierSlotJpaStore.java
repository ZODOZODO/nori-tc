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
import com.nori.tc.db.core.work.store.TcWorkCarrierSlotStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrierSlot;
import com.nori.tc.db.domain.work.TcWorkCarrierSlot;
import com.nori.tc.db.jpa.common.entity.work.TcWorkCarrierSlotEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkCarrierSlotEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkCarrierSlotJpaRepository;

/**
 * tc_work_carrier_slot JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> (work_carrier_key, slot_no) 유니크 키로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcWorkCarrierSlotJpaStore implements TcWorkCarrierSlotStore {

    private final TcWorkCarrierSlotJpaRepository repository;
    private final TcWorkCarrierSlotEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcWorkCarrierSlotJpaStore(TcWorkCarrierSlotJpaRepository repository, TcWorkCarrierSlotEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkCarrierSlot upsert(UpsertTcWorkCarrierSlot command) {
        validateCommand(command);

        try {
            final long workCarrierKey = command.workCarrierKey();
            final int slotNo = command.slotNo();

            TcWorkCarrierSlotEntity entity = repository.findByWorkCarrierKeyAndSlotNo(workCarrierKey, slotNo)
                    .orElseGet(() -> TcWorkCarrierSlotEntity.newEntity(workCarrierKey, slotNo));

            mapper.updateEntity(command, entity);

            TcWorkCarrierSlotEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_carrier_slot] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier_slot] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkCarrierSlot> findByWorkCarrierKeySlotNo(long workCarrierKey, int slotNo) {
        if (workCarrierKey <= 0) {
            throw new IllegalArgumentException("workCarrierKey must be > 0");
        }
        if (slotNo < 1) {
            throw new IllegalArgumentException("slotNo must be >= 1");
        }
        try {
            return repository.findByWorkCarrierKeyAndSlotNo(workCarrierKey, slotNo).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier_slot] findByWorkCarrierKeySlotNo failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkCarrierSlot> findAllByWorkCarrierKey(long workCarrierKey, PageRequest page) {
        if (workCarrierKey <= 0) {
            throw new IllegalArgumentException("workCarrierKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkCarrierSlotEntity> cq = cb.createQuery(TcWorkCarrierSlotEntity.class);
            Root<TcWorkCarrierSlotEntity> root = cq.from(TcWorkCarrierSlotEntity.class);

            Predicate predicate = cb.equal(root.get("workCarrierKey"), workCarrierKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("slotNo")));

            TypedQuery<TcWorkCarrierSlotEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier_slot] findAllByWorkCarrierKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkCarrierKeySlotNo(long workCarrierKey, int slotNo) {
        if (workCarrierKey <= 0) {
            throw new IllegalArgumentException("workCarrierKey must be > 0");
        }
        if (slotNo < 1) {
            throw new IllegalArgumentException("slotNo must be >= 1");
        }
        try {
            repository.findByWorkCarrierKeyAndSlotNo(workCarrierKey, slotNo).ifPresent(repository::delete);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier_slot] deleteByWorkCarrierKeySlotNo failed", e);
        }
    }

    private void validateCommand(UpsertTcWorkCarrierSlot command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workCarrierKey() <= 0) throw new IllegalArgumentException("command.workCarrierKey must be > 0");
        if (command.slotNo() < 1) throw new IllegalArgumentException("command.slotNo must be >= 1");
        if (command.slotState() == null || command.slotState().isBlank()) {
            throw new IllegalArgumentException("command.slotState must not be null/blank");
        }
    }
}
