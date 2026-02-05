package com.nori.tc.db.jpa.common.store.model;

import java.util.ArrayList;
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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.model.NewTcModelWorkflow;
import com.nori.tc.db.core.model.TcModelWorkflowSearchCriteria;
import com.nori.tc.db.core.model.TcModelWorkflowStore;
import com.nori.tc.db.core.model.UpdateTcModelWorkflow;
import com.nori.tc.db.domain.model.TcModelWorkflow;
import com.nori.tc.db.jpa.common.entity.model.TcModelWorkflowEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelWorkflowEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelWorkflowJpaRepository;

/**
 * tc_model_workflow JPA Store 구현체.
 *
 * <p>
 * <b>주요 기능:</b>
 * <ul>
 * <li><b>Create/Update 분리:</b> 생성과 수정 Command가 분리되어 있으며, MapStruct로 매핑합니다.</li>
 * <li><b>동적 검색:</b> Criteria API로 modelKey/workflowName/messageName 조합 검색을 지원합니다.</li>
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
    public TcModelWorkflow create(NewTcModelWorkflow command) {
        validateCreate(command);

        try {
            // 1. 필수 Business Key(modelKey, workflowName, messageName)로 초기 엔티티 생성
            TcModelWorkflowEntity entity = TcModelWorkflowEntity.newEntity(
                    command.modelKey(),
                    command.workflowName(),
                    command.messageName()
            );

            // 2. 나머지 필드 자동 매핑
            mapper.updateFromNew(command, entity);

            // 3. 저장 및 반환
            TcModelWorkflowEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_workflow] create failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_workflow] create failed", e);
        }
    }

    @Override
    @Transactional
    public TcModelWorkflow update(UpdateTcModelWorkflow command) {
        validateUpdate(command);

        try {
            // 1. 조회 (없으면 예외)
            TcModelWorkflowEntity entity = repository.findById(command.workflowKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_model_workflow] not found: workflowKey=" + command.workflowKey()
                    ));

            // 2. Dirty Checking용 필드 업데이트
            mapper.updateFromUpdate(command, entity);

            // 3. 저장 및 반환
            TcModelWorkflowEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DbEntityNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_workflow] update failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "[tc_model_workflow] update failed: workflowKey=" + command.workflowKey(),
                    e
            );
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
    public List<TcModelWorkflow> findAll(TcModelWorkflowSearchCriteria criteria, PageRequest page) {
        final TcModelWorkflowSearchCriteria c = (criteria == null) ? TcModelWorkflowSearchCriteria.empty() : criteria;
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelWorkflowEntity> cq = cb.createQuery(TcModelWorkflowEntity.class);
            Root<TcModelWorkflowEntity> root = cq.from(TcModelWorkflowEntity.class);

            List<Predicate> predicates = new ArrayList<>();

            if (c.modelKey() != null) {
                predicates.add(cb.equal(root.get("modelKey"), c.modelKey()));
            }
            if (c.workflowNameLike() != null && !c.workflowNameLike().isBlank()) {
                String keyword = c.workflowNameLike().trim().toLowerCase();
                String pattern = "%" + escapeLike(keyword) + "%";
                predicates.add(cb.like(cb.lower(root.get("workflowName")), pattern, '\\'));
            }
            if (c.messageNameLike() != null && !c.messageNameLike().isBlank()) {
                String keyword = c.messageNameLike().trim().toLowerCase();
                String pattern = "%" + escapeLike(keyword) + "%";
                predicates.add(cb.like(cb.lower(root.get("messageName")), pattern, '\\'));
            }

            cq.select(root);
            if (!predicates.isEmpty()) {
                cq.where(predicates.toArray(Predicate[]::new));
            }

            cq.orderBy(cb.desc(root.get("workflowKey")));

            TypedQuery<TcModelWorkflowEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_workflow] findAll failed", e);
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

    private void validateCreate(NewTcModelWorkflow command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
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

    private void validateUpdate(UpdateTcModelWorkflow command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workflowKey() <= 0) throw new IllegalArgumentException("command.workflowKey must be > 0");
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

    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
