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
import com.nori.tc.db.core.work.store.TcWorkProcessJobStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessJob;
import com.nori.tc.db.domain.work.TcWorkProcessJob;
import com.nori.tc.db.jpa.common.entity.work.TcWorkProcessJobEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkProcessJobEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkProcessJobJpaRepository;

/**
 * tc_work_processjob JPA Store 구현체.
 *
 * <p>
 * 설계 전략:
 * <ul>
 *     <li><b>Upsert:</b> process_job_key가 있으면 PK 기준, 없으면 (control_job_key, processjob_id) 유니크 키로 조회 후 저장한다.</li>
 *     <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용한다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcWorkProcessJobJpaStore implements TcWorkProcessJobStore {

    private final TcWorkProcessJobJpaRepository repository;
    private final TcWorkProcessJobEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcWorkProcessJobJpaStore(
            TcWorkProcessJobJpaRepository repository,
            TcWorkProcessJobEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkProcessJob upsert(UpsertTcWorkProcessJob command) {
        validateCommand(command);

        try {
            TcWorkProcessJobEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcWorkProcessJobEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_processjob] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessJob> findByProcessJobKey(long processJobKey) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        try {
            return repository.findById(processJobKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] findByProcessJobKey failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessJob> findByControlJobKeyAndProcessjobId(long controlJobKey, String processjobId) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        if (processjobId == null || processjobId.isBlank()) {
            throw new IllegalArgumentException("processjobId must not be null/blank");
        }
        try {
            return repository.findByControlJobKeyAndProcessjobId(controlJobKey, processjobId)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] findByControlJobKeyAndProcessjobId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkProcessJob> findAllByControlJobKey(long controlJobKey, PageRequest page) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkProcessJobEntity> cq = cb.createQuery(TcWorkProcessJobEntity.class);
            Root<TcWorkProcessJobEntity> root = cq.from(TcWorkProcessJobEntity.class);

            Predicate predicate = cb.equal(root.get("controlJobKey"), controlJobKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.desc(root.get("processJobKey")));

            TypedQuery<TcWorkProcessJobEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] findAllByControlJobKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByProcessJobKey(long processJobKey) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        try {
            repository.deleteById(processJobKey);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] deleteByProcessJobKey failed", e);
        }
    }

    private void validateCommand(UpsertTcWorkProcessJob command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.processJobKey() != null && command.processJobKey() <= 0) {
            throw new IllegalArgumentException("command.processJobKey must be > 0 when provided");
        }
        if (command.controlJobKey() <= 0) {
            throw new IllegalArgumentException("command.controlJobKey must be > 0");
        }
        if (command.processjobId() == null || command.processjobId().isBlank()) {
            throw new IllegalArgumentException("command.processjobId must not be null/blank");
        }
        if (command.processjobState() == null) {
            throw new IllegalArgumentException("command.processjobState must not be null");
        }
        if (command.recipeId() == null || command.recipeId().isBlank()) {
            throw new IllegalArgumentException("command.recipeId must not be null/blank");
        }
    }

    private TcWorkProcessJobEntity resolveEntity(UpsertTcWorkProcessJob command) {
        if (command.processJobKey() != null) {
            return repository.findById(command.processJobKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_work_processjob] not found: processJobKey=" + command.processJobKey()
                    ));
        }

        return repository.findByControlJobKeyAndProcessjobId(command.controlJobKey(), command.processjobId())
                .orElseGet(() -> TcWorkProcessJobEntity.newEntity(command.controlJobKey(), command.processjobId()));
    }
}
