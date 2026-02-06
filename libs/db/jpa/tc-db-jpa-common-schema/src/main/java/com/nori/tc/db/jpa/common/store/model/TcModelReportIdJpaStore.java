package com.nori.tc.db.jpa.common.store.model;

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
import com.nori.tc.db.core.model.store.TcModelReportIdStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelReportId;
import com.nori.tc.db.domain.model.TcModelReportId;
import com.nori.tc.db.jpa.common.entity.model.TcModelReportIdEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelReportIdEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelReportIdJpaRepository;

/**
 * tc_model_reportid JPA Store 구현체.
 */
@Repository
public class TcModelReportIdJpaStore implements TcModelReportIdStore {

    private final TcModelReportIdJpaRepository repository;
    private final TcModelReportIdEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcModelReportIdJpaStore(TcModelReportIdJpaRepository repository, TcModelReportIdEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void upsert(UpsertTcModelReportId command) {
        validateUpsert(command);

        try {
            TcModelReportIdEntity entity = repository.findByModelKeyAndReportId(command.modelKey(), command.reportId())
                    .orElseGet(TcModelReportIdEntity::new);

            mapper.updateFromUpsert(command, entity);
            repository.save(entity);
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_reportid] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_reportid] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelReportId> findByReportKey(long reportKey) {
        if (reportKey <= 0) {
            throw new IllegalArgumentException("reportKey must be > 0");
        }
        try {
            return repository.findById(reportKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_reportid] findByReportKey failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelReportId> findByModelKeyAndReportId(long modelKey, String reportId) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (reportId == null || reportId.isBlank()) {
            throw new IllegalArgumentException("reportId must not be null/blank");
        }

        try {
            return repository.findByModelKeyAndReportId(modelKey, reportId)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_reportid] findByModelKeyAndReportId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelReportId> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelReportIdEntity> cq = cb.createQuery(TcModelReportIdEntity.class);
            Root<TcModelReportIdEntity> root = cq.from(TcModelReportIdEntity.class);

            cq.where(cb.equal(root.get("modelKey"), modelKey));
            cq.orderBy(cb.asc(root.get("reportKey")));

            TypedQuery<TcModelReportIdEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_reportid] findAllByModelKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByReportKey(long reportKey) {
        if (reportKey <= 0) {
            throw new IllegalArgumentException("reportKey must be > 0");
        }
        try {
            repository.deleteById(reportKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_reportid] deleteByReportKey failed: reportKey=" + reportKey, e);
        }
    }

    private void validateUpsert(UpsertTcModelReportId command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.reportId() == null || command.reportId().isBlank()) {
            throw new IllegalArgumentException("command.reportId must not be null/blank");
        }
    }
}