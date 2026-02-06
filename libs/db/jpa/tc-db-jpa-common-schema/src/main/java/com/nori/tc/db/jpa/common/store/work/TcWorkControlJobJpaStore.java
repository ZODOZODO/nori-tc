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
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.work.store.TcWorkControlJobStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkControlJob;
import com.nori.tc.db.domain.work.TcWorkControlJob;
import com.nori.tc.db.jpa.common.entity.work.TcWorkControlJobEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkControlJobEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkControlJobJpaRepository;

/**
 * tc_work_controljob JPA Store 구현체.
 *
 * <p>
 * 설계 전략:
 * <ul>
 *     <li><b>Upsert:</b> control_job_key가 있으면 PK 기준, 없으면 (work_key, controljob_id) 유니크 키로 조회 후 저장한다.</li>
 *     <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용한다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcWorkControlJobJpaStore implements TcWorkControlJobStore {

    private final TcWorkControlJobJpaRepository repository;
    private final TcWorkControlJobEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcWorkControlJobJpaStore(
            TcWorkControlJobJpaRepository repository,
            TcWorkControlJobEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkControlJob upsert(UpsertTcWorkControlJob command) {
        validateCommand(command);

        try {
            TcWorkControlJobEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcWorkControlJobEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_controljob] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkControlJob> findByControlJobKey(long controlJobKey) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        try {
            return repository.findById(controlJobKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] findByControlJobKey failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkControlJob> findByWorkKeyAndControljobId(long workKey, String controljobId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (controljobId == null || controljobId.isBlank()) {
            throw new IllegalArgumentException("controljobId must not be null/blank");
        }
        try {
            return repository.findByWorkKeyAndControljobId(workKey, controljobId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] findByWorkKeyAndControljobId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkControlJob> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkControlJobEntity> cq = cb.createQuery(TcWorkControlJobEntity.class);
            Root<TcWorkControlJobEntity> root = cq.from(TcWorkControlJobEntity.class);

            Predicate predicate = cb.equal(root.get("workKey"), workKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("controlJobKey")));

            TypedQuery<TcWorkControlJobEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] findAllByWorkKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByControlJobKey(long controlJobKey) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        try {
            repository.deleteById(controlJobKey);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] deleteByControlJobKey failed", e);
        }
    }

    private void validateCommand(UpsertTcWorkControlJob command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.controlJobKey() != null && command.controlJobKey() <= 0) {
            throw new IllegalArgumentException("command.controlJobKey must be > 0 when provided");
        }
        if (command.workKey() <= 0) {
            throw new IllegalArgumentException("command.workKey must be > 0");
        }
        if (command.controljobId() == null || command.controljobId().isBlank()) {
            throw new IllegalArgumentException("command.controljobId must not be null/blank");
        }
        if (command.controljobState() == null) {
            throw new IllegalArgumentException("command.controljobState must not be null");
        }
    }

    private TcWorkControlJobEntity resolveEntity(UpsertTcWorkControlJob command) {
        if (command.controlJobKey() != null) {
            return repository.findById(command.controlJobKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_work_controljob] not found: controlJobKey=" + command.controlJobKey()
                    ));
        }

        return repository.findByWorkKeyAndControljobId(command.workKey(), command.controljobId())
                .orElseGet(() -> TcWorkControlJobEntity.newEntity(command.workKey(), command.controljobId()));
    }
}
