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
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.model.TcModelWorkflowStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelWorkflow;
import com.nori.tc.domain.model.TcModelWorkflow;
import com.nori.tc.db.jpa.common.entity.model.TcModelWorkflowEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelWorkflowEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelWorkflowJpaRepository;

/**
 * tc_model_workflow JPA Store 구현체.
 *
 * <p>
 * <b>주요 기능:</b>
 * <ul>
 * <li><b>Upsert:</b> 워크플로 키 또는 유니크 키로 존재 여부를 확인한 뒤 생성/갱신을 수행합니다.</li>
 * <li><b>목록 조회:</b> model_key 기준으로 최신 workflow_key DESC 정렬 + 페이징을 제공합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelWorkflowJpaStore implements TcModelWorkflowStore {

    private final TcModelWorkflowJpaRepository repository;
    private final TcModelWorkflowEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcModelWorkflowJpaStore(TcModelWorkflowJpaRepository repository, TcModelWorkflowEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelWorkflow upsert(UpsertTcModelWorkflow command) {
        validateUpsert(command);

        try {
            TcModelWorkflowEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcModelWorkflowEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DbEntityNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_workflow] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_workflow] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelWorkflow> findByWorkflowKey(long workflowKey) {
        if (workflowKey <= 0) {
            throw new IllegalArgumentException("workflowKey must be > 0");
        }
        try {
            return repository.findById(workflowKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_workflow] findByWorkflowKey failed: workflowKey=" + workflowKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelWorkflow> findByModelKeyAndWorkflowNameAndMessageName(
            long modelKey,
            String workflowName,
            String messageName
    ) {
        if (modelKey <= 0) throw new IllegalArgumentException("modelKey must be > 0");
        if (workflowName == null || workflowName.isBlank()) throw new IllegalArgumentException("workflowName must not be null/blank");
        if (messageName == null || messageName.isBlank()) throw new IllegalArgumentException("messageName must not be null/blank");

        try {
            return repository.findByModelKeyAndWorkflowNameAndMessageName(modelKey, workflowName, messageName)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_workflow] findByUnique failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelWorkflow> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelWorkflowEntity> cq = cb.createQuery(TcModelWorkflowEntity.class);
            Root<TcModelWorkflowEntity> root = cq.from(TcModelWorkflowEntity.class);

            cq.select(root);
            cq.where(cb.equal(root.get("modelKey"), modelKey));
            cq.orderBy(cb.desc(root.get("workflowKey")));

            TypedQuery<TcModelWorkflowEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_workflow] findAllByModelKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByWorkflowKey(long workflowKey) {
        if (workflowKey <= 0) {
            throw new IllegalArgumentException("workflowKey must be > 0");
        }
        try {
            repository.deleteById(workflowKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_workflow] deleteByWorkflowKey failed: workflowKey=" + workflowKey, e);
        }
    }

    private void validateUpsert(UpsertTcModelWorkflow command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workflowKey() != null && command.workflowKey() <= 0) {
            throw new IllegalArgumentException("command.workflowKey must be > 0 when provided");
        }
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.workflowName() == null || command.workflowName().isBlank()) {
            throw new IllegalArgumentException("command.workflowName must not be null/blank");
        }
        if (command.messageName() == null || command.messageName().isBlank()) {
            throw new IllegalArgumentException("command.messageName must not be null/blank");
        }
        if (command.actionName() == null || command.actionName().isBlank()) {
            throw new IllegalArgumentException("command.actionName must not be null/blank");
        }
    }

    private TcModelWorkflowEntity resolveEntity(UpsertTcModelWorkflow command) {
        if (command.workflowKey() != null) {
            return repository.findById(command.workflowKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_model_workflow] not found: workflowKey=" + command.workflowKey()
                    ));
        }

        return repository.findByModelKeyAndWorkflowNameAndMessageName(
                        command.modelKey(),
                        command.workflowName(),
                        command.messageName()
                )
                .orElseGet(() -> TcModelWorkflowEntity.newEntity(
                        command.modelKey(),
                        command.workflowName(),
                        command.messageName()
                ));
    }
}