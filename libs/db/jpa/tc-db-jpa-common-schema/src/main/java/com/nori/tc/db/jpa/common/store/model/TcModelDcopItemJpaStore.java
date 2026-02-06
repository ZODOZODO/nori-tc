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
import com.nori.tc.db.core.model.store.TcModelDcopItemStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelDcopItem;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import com.nori.tc.db.jpa.common.entity.model.TcModelDcopItemEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelDcopItemEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelDcopItemJpaRepository;

/**
 * tc_model_dcop_item JPA Store 구현체.
 */
@Repository
public class TcModelDcopItemJpaStore implements TcModelDcopItemStore {

    private final TcModelDcopItemJpaRepository repository;
    private final TcModelDcopItemEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcModelDcopItemJpaStore(TcModelDcopItemJpaRepository repository, TcModelDcopItemEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelDcopItem upsert(UpsertTcModelDcopItem command) {
        validateUpsert(command);

        try {
            TcModelDcopItemEntity entity = repository.findByModelKeyAndDcopItemName(command.modelKey(), command.dcopItemName())
                    .orElseGet(TcModelDcopItemEntity::new);

            // 1. 엔티티 필드 업데이트 (명칭 변경 반영)
            mapper.updateEntity(command, entity);

            // 2. 저장 후 도메인 모델로 변환하여 반환
            TcModelDcopItemEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_dcop_item] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_dcop_item] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelDcopItem> findByModelKeyAndName(long modelKey, String dcopItemName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (dcopItemName == null || dcopItemName.isBlank()) {
            throw new IllegalArgumentException("dcopItemName must not be null/blank");
        }

        try {
            return repository.findByModelKeyAndDcopItemName(modelKey, dcopItemName)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_dcop_item] findByModelKeyAndName failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelDcopItem> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelDcopItemEntity> cq = cb.createQuery(TcModelDcopItemEntity.class);
            Root<TcModelDcopItemEntity> root = cq.from(TcModelDcopItemEntity.class);

            cq.select(root).where(cb.equal(root.get("modelKey"), modelKey));
            cq.orderBy(cb.asc(root.get("orderRule")), cb.asc(root.get("dcopItemName")));

            TypedQuery<TcModelDcopItemEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_dcop_item] findAllByModelKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByModelKeyAndName(long modelKey, String dcopItemName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (dcopItemName == null || dcopItemName.isBlank()) {
            throw new IllegalArgumentException("dcopItemName must not be null/blank");
        }
        try {
            repository.deleteByModelKeyAndDcopItemName(modelKey, dcopItemName);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_dcop_item] deleteByModelKeyAndName failed", e);
        }
    }

    private void validateUpsert(UpsertTcModelDcopItem command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.dcopItemName() == null || command.dcopItemName().isBlank()) {
            throw new IllegalArgumentException("command.dcopItemName must not be null/blank");
        }
    }
}