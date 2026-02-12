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
import com.nori.tc.db.core.work.store.TcWorkStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWork;
import com.nori.tc.db.domain.work.TcWork;
import com.nori.tc.db.jpa.common.entity.work.TcWorkEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkJpaRepository;

/**
 * tc_work JPA Store 구현체.
 */
@Repository
public class TcWorkJpaStore implements TcWorkStore {

    private final TcWorkJpaRepository repository;
    private final TcWorkEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcWorkJpaStore(TcWorkJpaRepository repository, TcWorkEntityMapper mapper) {
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
    public TcWork upsert(UpsertTcWork command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateUpsert(command);

        try {
            TcWorkEntity entity = resolveEntity(command);
            mapper.updateFromUpsert(command, entity);

            TcWorkEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWork> findByWorkKey(long workKey) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        try {
            return repository.findById(workKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] findByWorkKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param workId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWork> findByEqpKeyAndWorkId(long eqpKey, String workId) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (workId == null || workId.isBlank()) {
            throw new IllegalArgumentException("workId must not be null/blank");
        }

        try {
            return repository.findByEqpKeyAndWorkId(eqpKey, workId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] findByEqpKeyAndWorkId failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcWork> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkEntity> cq = cb.createQuery(TcWorkEntity.class);
            Root<TcWorkEntity> root = cq.from(TcWorkEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("eqpKey"), eqpKey))
                    .orderBy(cb.desc(root.get("workKey")));

            TypedQuery<TcWorkEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] findAllByEqpKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByWorkKey(long workKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        try {
            repository.deleteById(workKey);
        } catch (EmptyResultDataAccessException ignore) {
            // 멱등 삭제: 존재하지 않아도 오류로 취급하지 않는다.
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work] deleteByWorkKey failed: workKey=" + workKey, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateUpsert(UpsertTcWork command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be > 0");
        if (command.workId() == null || command.workId().isBlank()) {
            throw new IllegalArgumentException("command.workId must not be null/blank");
        }
        if (command.workState() == null) {
            throw new IllegalArgumentException("command.workState must not be null");
        }
        if (command.stepSeq() != null && command.stepSeq() < 0) {
            throw new IllegalArgumentException("command.stepSeq must be >= 0 when provided");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private TcWorkEntity resolveEntity(UpsertTcWork command) {
        if (command.workKey() != null) {
            if (command.workKey() <= 0) {
                throw new IllegalArgumentException("workKey must be > 0 when provided");
            }
            return repository.findById(command.workKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_work] not found: workKey=" + command.workKey()
                    ));
        }

        return repository.findByEqpKeyAndWorkId(command.eqpKey(), command.workId())
                .orElseGet(() -> TcWorkEntity.newEntity(command.eqpKey(), command.workId()));
    }
}
