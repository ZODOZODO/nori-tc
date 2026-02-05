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
import com.nori.tc.db.core.model.NewTcModel;
import com.nori.tc.db.core.model.TcModelSearchCriteria;
import com.nori.tc.db.core.model.TcModelStore;
import com.nori.tc.db.core.model.UpdateTcModel;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.jpa.common.entity.model.TcModelEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelJpaRepository;

/**
 * tc_model JPA Store 구현체.
 *
 * <p>
 * <b>주요 기능:</b>
 * <ul>
 * <li><b>Create/Update 분리:</b> 생성과 수정 Command가 분리되어 있으며, MapStruct를 통해 각각 최적화된 매핑을 수행합니다.</li>
 * <li><b>동적 검색:</b> Criteria API를 사용하여 modelName(LIKE), commInterface, status 등의 조합 검색을 지원합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelJpaStore implements TcModelStore {

    private final TcModelJpaRepository repository;
    private final TcModelEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcModelJpaStore(TcModelJpaRepository repository, TcModelEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModel create(NewTcModel command) {
        validateCreate(command);

        try {
            // 1. 필수 Business Key(name, version)로 초기 엔티티 생성
            TcModelEntity entity = TcModelEntity.newEntity(command.modelName(), command.modelVersion());

            // 2. 나머지 필드 자동 매핑
            mapper.updateFromNew(command, entity);

            // 3. 저장 및 반환
            TcModelEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model] create failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] create failed", e);
        }
    }

    @Override
    @Transactional
    public TcModel update(UpdateTcModel command) {
        validateUpdate(command);

        try {
            // 1. 조회 (없으면 예외)
            TcModelEntity entity = repository.findById(command.modelKey())
                    .orElseThrow(() -> new DbEntityNotFoundException("[tc_model] not found: modelKey=" + command.modelKey()));

            // 2. Dirty Checking용 필드 업데이트
            mapper.updateFromUpdate(command, entity);

            // 3. 저장 및 반환
            TcModelEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DbEntityNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model] update failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] update failed: modelKey=" + command.modelKey(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByModelKey(long modelKey) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        try {
            return repository.findById(modelKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] findByModelKey failed: modelKey=" + modelKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByNameVersion(String modelName, String modelVersion) {
        if (modelName == null || modelName.isBlank()) throw new IllegalArgumentException("modelName must not be null/blank");
        if (modelVersion == null || modelVersion.isBlank()) throw new IllegalArgumentException("modelVersion must not be null/blank");

        try {
            return repository.findByModelNameAndModelVersion(modelName, modelVersion).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] findByNameVersion failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModel> findAll(TcModelSearchCriteria criteria, PageRequest page) {
        final TcModelSearchCriteria c = (criteria == null) ? TcModelSearchCriteria.empty() : criteria;
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelEntity> cq = cb.createQuery(TcModelEntity.class);
            Root<TcModelEntity> root = cq.from(TcModelEntity.class);

            List<Predicate> predicates = new ArrayList<>();

            // --- LIKE 검색 (contains, case-insensitive) ---
            if (c.modelNameLike() != null && !c.modelNameLike().isBlank()) {
                String keyword = c.modelNameLike().trim().toLowerCase();
                String pattern = "%" + escapeLike(keyword) + "%";
                predicates.add(cb.like(cb.lower(root.get("modelName")), pattern, '\\'));
            }
            if (c.commInterface() != null) {
                predicates.add(cb.equal(root.get("commInterface"), c.commInterface()));
            }
            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }

            cq.select(root);
            if (!predicates.isEmpty()) {
                cq.where(predicates.toArray(Predicate[]::new));
            }

            cq.orderBy(cb.desc(root.get("modelKey")));

            TypedQuery<TcModelEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] findAll failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByModelKey(long modelKey) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        try {
            repository.deleteById(modelKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] deleteByModelKey failed: modelKey=" + modelKey, e);
        }
    }

    private void validateCreate(NewTcModel command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelName() == null || command.modelName().isBlank()) throw new IllegalArgumentException("command.modelName must not be null/blank");
        if (command.modelVersion() == null || command.modelVersion().isBlank()) throw new IllegalArgumentException("command.modelVersion must not be null/blank");
        if (command.commInterface() == null) throw new IllegalArgumentException("command.commInterface must not be null");
        if (command.status() == null) throw new IllegalArgumentException("command.status must not be null");
    }

    private void validateUpdate(UpdateTcModel command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.modelName() == null || command.modelName().isBlank()) throw new IllegalArgumentException("command.modelName must not be null/blank");
        if (command.modelVersion() == null || command.modelVersion().isBlank()) throw new IllegalArgumentException("command.modelVersion must not be null/blank");
        if (command.commInterface() == null) throw new IllegalArgumentException("command.commInterface must not be null");
        if (command.status() == null) throw new IllegalArgumentException("command.status must not be null");
    }

    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
