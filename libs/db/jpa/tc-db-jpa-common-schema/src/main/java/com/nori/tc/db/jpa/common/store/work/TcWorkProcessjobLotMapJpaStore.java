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
import com.nori.tc.db.core.work.store.TcWorkProcessjobLotMapStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessjobLotMap;
import com.nori.tc.db.domain.work.TcWorkProcessjobLotMap;
import com.nori.tc.db.jpa.common.entity.work.TcWorkProcessjobLotMapEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkProcessjobLotMapEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkProcessjobLotMapJpaRepository;

/**
 * tc_work_processjob_lot_map JPA Store 구현체.
 */
@Repository
public class TcWorkProcessjobLotMapJpaStore implements TcWorkProcessjobLotMapStore {

    private final TcWorkProcessjobLotMapJpaRepository repository;
    private final TcWorkProcessjobLotMapEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcWorkProcessjobLotMapJpaStore(
            TcWorkProcessjobLotMapJpaRepository repository,
            TcWorkProcessjobLotMapEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcWorkProcessjobLotMap upsert(UpsertTcWorkProcessjobLotMap command) {
        validateUpsert(command);

        try {
            TcWorkProcessjobLotMapEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcWorkProcessjobLotMapEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_processjob_lot_map] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob_lot_map] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessjobLotMap> findByPjLotMapKey(long pjLotMapKey) {
        if (pjLotMapKey <= 0) {
            throw new IllegalArgumentException("pjLotMapKey must be > 0");
        }
        try {
            return repository.findById(pjLotMapKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob_lot_map] findByPjLotMapKey failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessjobLotMap> findByProcessJobKeyAndWorkLotKey(long processJobKey, long workLotKey) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        if (workLotKey <= 0) {
            throw new IllegalArgumentException("workLotKey must be > 0");
        }
        try {
            return repository.findByProcessJobKeyAndWorkLotKey(processJobKey, workLotKey)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob_lot_map] findByUniqueKey failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcWorkProcessjobLotMap> findAllByProcessJobKey(long processJobKey, PageRequest page) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkProcessjobLotMapEntity> cq = cb.createQuery(TcWorkProcessjobLotMapEntity.class);
            Root<TcWorkProcessjobLotMapEntity> root = cq.from(TcWorkProcessjobLotMapEntity.class);

            // processJobKey로 필터링하고 pj_lot_map_key 오름차순으로 정렬한다.
            cq.select(root).where(cb.equal(root.get("processJobKey"), processJobKey));
            cq.orderBy(cb.asc(root.get("pjLotMapKey")));

            TypedQuery<TcWorkProcessjobLotMapEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob_lot_map] findAllByProcessJobKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByPjLotMapKey(long pjLotMapKey) {
        if (pjLotMapKey <= 0) {
            throw new IllegalArgumentException("pjLotMapKey must be > 0");
        }
        try {
            repository.deleteById(pjLotMapKey);
        } catch (EmptyResultDataAccessException ignore) {
            // 멱등 삭제: 존재하지 않아도 오류로 취급하지 않는다.
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob_lot_map] deleteByPjLotMapKey failed", e);
        }
    }

    private void validateUpsert(UpsertTcWorkProcessjobLotMap command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.pjLotMapKey() != null && command.pjLotMapKey() <= 0) {
            throw new IllegalArgumentException("command.pjLotMapKey must be > 0 when provided");
        }
        if (command.processJobKey() <= 0) {
            throw new IllegalArgumentException("command.processJobKey must be > 0");
        }
        if (command.workLotKey() <= 0) {
            throw new IllegalArgumentException("command.workLotKey must be > 0");
        }
    }

    private TcWorkProcessjobLotMapEntity resolveEntity(UpsertTcWorkProcessjobLotMap command) {
        if (command.pjLotMapKey() != null) {
            return repository.findById(command.pjLotMapKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_work_processjob_lot_map] not found: pjLotMapKey=" + command.pjLotMapKey()
                    ));
        }

        return repository.findByProcessJobKeyAndWorkLotKey(command.processJobKey(), command.workLotKey())
                .orElseGet(() -> TcWorkProcessjobLotMapEntity.newEntity(
                        command.processJobKey(),
                        command.workLotKey()
                ));
    }
}
