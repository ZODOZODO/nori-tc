package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.model.store.TcModelMdfStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.jpa.common.entity.model.TcModelMdfEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelMdfEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelMdfJpaRepository;

/**
 * tc_model_mdf JPA Store 구현체.
 *
 * <p>
 * <b>주요 기능:</b>
 * <ul>
 * <li><b>Upsert:</b> MDF 키 또는 유니크 키로 존재 여부를 확인한 뒤 생성/갱신을 수행합니다.</li>
 * <li><b>목록 조회:</b> 특정 model_version_key 기준으로 최신 mdf_key DESC 정렬 + 페이징을 제공합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelMdfJpaStore implements TcModelMdfStore {

    private final TcModelMdfJpaRepository repository;
    private final TcModelMdfEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcModelMdfJpaStore(TcModelMdfJpaRepository repository, TcModelMdfEntityMapper mapper) {
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
    public TcModelMdf upsert(UpsertTcModelMdf command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateUpsert(command);

        try {
            // 1. PK 또는 유니크 키 기반으로 대상 엔티티 결정
            TcModelMdfEntity entity = resolveEntity(command);

            // 2. Dirty Checking용 필드 업데이트
            mapper.updateFromUpsert(command, entity);

            // 3. 저장 및 반환
            TcModelMdfEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DbEntityNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_mdf] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mdfKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByMdfKey(long mdfKey) {
        if (mdfKey <= 0) {
            throw new IllegalArgumentException("mdfKey must be > 0");
        }
        try {
            return repository.findById(mdfKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] findByMdfKey failed: mdfKey=" + mdfKey, e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param mdfName DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByModelVersionKeyAndName(long modelVersionKey, String mdfName) {
        if (modelVersionKey <= 0) throw new IllegalArgumentException("modelVersionKey must be > 0");
        if (mdfName == null || mdfName.isBlank()) throw new IllegalArgumentException("mdfName must not be null/blank");

        try {
            return repository.findByModelVersionKeyAndMdfName(modelVersionKey, mdfName).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] findByModelVersionKeyAndName failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelMdf> findAllByModelVersionKey(long modelVersionKey, PageRequest page) {
        if (modelVersionKey <= 0) throw new IllegalArgumentException("modelVersionKey must be > 0");
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            TypedQuery<TcModelMdfEntity> query = em.createQuery(
                    "SELECT e FROM TcModelMdfEntity e WHERE e.modelVersionKey = :modelVersionKey ORDER BY e.mdfKey DESC",
                    TcModelMdfEntity.class
            );
            query.setParameter("modelVersionKey", modelVersionKey);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());
            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] findAllByModelVersionKey failed: modelVersionKey=" + modelVersionKey, e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mdfKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByMdfKey(long mdfKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (mdfKey <= 0) {
            throw new IllegalArgumentException("mdfKey must be > 0");
        }
        try {
            repository.deleteById(mdfKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] deleteByMdfKey failed: mdfKey=" + mdfKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateUpsert(UpsertTcModelMdf command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.mdfKey() != null && command.mdfKey() <= 0) {
            throw new IllegalArgumentException("command.mdfKey must be > 0 when provided");
        }
        if (command.modelVersionKey() <= 0) throw new IllegalArgumentException("command.modelVersionKey must be > 0");
        if (command.mdfName() == null || command.mdfName().isBlank()) {
            throw new IllegalArgumentException("command.mdfName must not be null/blank");
        }
        if (command.mdfFile() == null || command.mdfFile().length == 0) {
            throw new IllegalArgumentException("command.mdfFile must not be null/empty");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private TcModelMdfEntity resolveEntity(UpsertTcModelMdf command) {
        if (command.mdfKey() != null) {
            return repository.findById(command.mdfKey())
                    .orElseThrow(() -> new DbEntityNotFoundException("[tc_model_mdf] not found: mdfKey=" + command.mdfKey()));
        }

        return repository.findByModelVersionKeyAndMdfName(command.modelVersionKey(), command.mdfName())
                .orElseGet(() -> TcModelMdfEntity.newEntity(command.modelVersionKey(), command.mdfName(), command.mdfFile()));
    }
}