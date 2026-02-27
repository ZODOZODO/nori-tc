package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
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
import com.nori.tc.db.core.model.store.TcModelStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.jpa.common.entity.model.TcModelEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelJpaRepository;

@Repository
public class TcModelJpaStore implements TcModelStore {

    private final EntityManager em;
    private final TcModelJpaRepository repository;
    private final TcModelEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param em DB JPA 계층 처리에 사용하는 입력 값
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcModelJpaStore(EntityManager em, TcModelJpaRepository repository, TcModelEntityMapper mapper) {
        this.em = em;
        this.repository = repository;
        this.mapper = mapper;
    }

    
    /**
     * DB JPA 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    @Override
    @Transactional
    public TcModel upsert(UpsertTcModel command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (command == null) throw new IllegalArgumentException("UpsertTcModel must not be null");
        if (command.modelName() == null || command.modelName().isBlank()) {
            throw new IllegalArgumentException("modelName must not be null/blank");
        }
        if (command.modelVersion() == null || command.modelVersion().isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be null/blank");
        }

        try {
            TcModelEntity entity = resolveEntity(command);
            mapper.updateFromUpsert(command, entity);
            if (entity.getModelKey() == null && command.createdBy() != null && !command.createdBy().isBlank()) {
                entity.setCreatedBy(command.createdBy());
            }
            TcModelEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModel> findByModelVersionKey(long modelVersionKey) {
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be positive");
        }
        try {
            return repository.findByModelVersionKey(modelVersionKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] findByModelVersionKey failed: modelVersionKey=" + modelVersionKey, e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelName 도메인 데이터 객체
     * @param modelVersion 도메인 데이터 객체
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModel> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelEntity> cq = cb.createQuery(TcModelEntity.class);
            Root<TcModelEntity> root = cq.from(TcModelEntity.class);

            cq.select(root);
            cq.orderBy(cb.desc(root.get("updatedAt")), cb.asc(root.get("modelName")));

            TypedQuery<TcModelEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] findAll failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByModelVersionKey(long modelVersionKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (modelVersionKey <= 0) {
            throw new IllegalArgumentException("modelVersionKey must be positive");
        }
        try {
            repository.deleteByModelVersionKey(modelVersionKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model] deleteByModelVersionKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private TcModelEntity resolveEntity(UpsertTcModel command) {
        Long modelKey = command.modelKey();
        if (modelKey != null && modelKey > 0) {
            return repository.findById(modelKey)
                    .orElseGet(() -> TcModelEntity.newEntity(command.modelName(), command.modelVersion()));
        }
        return repository.findByModelNameAndModelVersion(command.modelName(), command.modelVersion())
                .orElseGet(() -> TcModelEntity.newEntity(command.modelName(), command.modelVersion()));
    }
}
