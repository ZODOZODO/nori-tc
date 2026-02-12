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
import com.nori.tc.db.core.model.store.TcModelSecsMessageStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.jpa.common.entity.model.TcModelSecsMessageEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelSecsMessageEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelSecsMessageJpaRepository;

/**
 * tc_model_secs_message JPA Store 구현체.
 *
 * <p>
 * <b>주요 기능:</b>
 * <ul>
 * <li><b>Upsert:</b> SECS 메시지 키 또는 유니크 키로 존재 여부를 확인한 뒤 생성/갱신을 수행합니다.</li>
 * <li><b>모델별 메시지 조회:</b> model_key 기반 목록 조회 및 유니크 키 조회를 지원합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelSecsMessageJpaStore implements TcModelSecsMessageStore {

    private final TcModelSecsMessageJpaRepository repository;
    private final TcModelSecsMessageEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcModelSecsMessageJpaStore(
            TcModelSecsMessageJpaRepository repository,
            TcModelSecsMessageEntityMapper mapper
    ) {
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
    public TcModelSecsMessage upsert(UpsertTcModelSecsMessage command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateUpsert(command);

        try {
            TcModelSecsMessageEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcModelSecsMessageEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DbEntityNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_secs_message] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param secsMsgKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSecsMessage> findBySecsMsgKey(long secsMsgKey) {
        if (secsMsgKey <= 0) {
            throw new IllegalArgumentException("secsMsgKey must be > 0");
        }
        try {
            return repository.findById(secsMsgKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] findBySecsMsgKey failed: secsMsgKey=" + secsMsgKey, e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param secsMsgName DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSecsMessage> findByModelKeyAndName(long modelKey, String secsMsgName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (secsMsgName == null || secsMsgName.isBlank()) {
            throw new IllegalArgumentException("secsMsgName must not be null/blank");
        }
        try {
            return repository.findByModelKeyAndSecsMsgName(modelKey, secsMsgName).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] findByModelKeyAndName failed", e);
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
    public List<TcModelSecsMessage> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;
        try {
            TypedQuery<TcModelSecsMessageEntity> query = em.createQuery(
                    "SELECT e FROM TcModelSecsMessageEntity e WHERE e.modelKey = :modelKey ORDER BY e.secsMsgKey ASC",
                    TcModelSecsMessageEntity.class
            );
            query.setParameter("modelKey", modelKey);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] findAllByModelKey failed: modelKey=" + modelKey, e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param secsMsgKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteBySecsMsgKey(long secsMsgKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (secsMsgKey <= 0) {
            throw new IllegalArgumentException("secsMsgKey must be > 0");
        }
        try {
            repository.deleteById(secsMsgKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] deleteBySecsMsgKey failed: secsMsgKey=" + secsMsgKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateUpsert(UpsertTcModelSecsMessage command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.secsMsgKey() != null && command.secsMsgKey() <= 0) {
            throw new IllegalArgumentException("command.secsMsgKey must be > 0 when provided");
        }
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.secsMsgName() == null || command.secsMsgName().isBlank()) {
            throw new IllegalArgumentException("command.secsMsgName must not be null/blank");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private TcModelSecsMessageEntity resolveEntity(UpsertTcModelSecsMessage command) {
        if (command.secsMsgKey() != null) {
            return repository.findById(command.secsMsgKey())
                    .orElseThrow(() -> new DbEntityNotFoundException("[tc_model_secs_message] not found: secsMsgKey=" + command.secsMsgKey()));
        }

        return repository.findByModelKeyAndSecsMsgName(command.modelKey(), command.secsMsgName())
                .orElseGet(() -> TcModelSecsMessageEntity.newEntity(command.modelKey(), command.secsMsgName()));
    }
}