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
import com.nori.tc.db.core.model.store.TcModelParamStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelParam;
import com.nori.tc.db.domain.model.TcModelParam;
import com.nori.tc.db.jpa.common.entity.model.TcModelParamEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelParamEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelParamJpaRepository;

/**
 * tc_model_param JPA Store 구현체.
 */
@Repository
public class TcModelParamJpaStore implements TcModelParamStore {

    private final TcModelParamJpaRepository repository;
    private final TcModelParamEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcModelParamJpaStore(TcModelParamJpaRepository repository, TcModelParamEntityMapper mapper) {
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
    public TcModelParam upsert(UpsertTcModelParam command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateUpsert(command);

        try {
            Optional<TcModelParamEntity> existing = repository.findByModelKeyAndParamName(
                    command.modelKey(),
                    command.paramName()
            );

            TcModelParamEntity entity = existing.orElseGet(
                    () -> TcModelParamEntity.newEntity(command.modelKey(), command.paramName())
            );

            mapper.updateEntity(command, entity);

            TcModelParamEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_param] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_param] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param paramName DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelParam> findByModelKeyAndName(long modelKey, String paramName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (paramName == null || paramName.isBlank()) {
            throw new IllegalArgumentException("paramName must not be null/blank");
        }

        try {
            return repository.findByModelKeyAndParamName(modelKey, paramName)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_param] findByModelKeyAndName failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelParam> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelParamEntity> cq = cb.createQuery(TcModelParamEntity.class);
            Root<TcModelParamEntity> root = cq.from(TcModelParamEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("modelKey"), modelKey))
                    .orderBy(cb.asc(root.get("modelParamKey")));

            TypedQuery<TcModelParamEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_param] findAllByModelKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelParamKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByModelParamKey(long modelParamKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (modelParamKey <= 0) {
            throw new IllegalArgumentException("modelParamKey must be > 0");
        }
        try {
            repository.deleteById(modelParamKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_param] deleteByModelParamKey failed: modelParamKey=" + modelParamKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateUpsert(UpsertTcModelParam command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.paramName() == null || command.paramName().isBlank()) throw new IllegalArgumentException("command.paramName must not be null/blank");
    }
}
