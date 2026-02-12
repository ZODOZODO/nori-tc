package com.nori.tc.db.jpa.common.store.work;

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
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.work.store.TcWorkControlJobStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkControlJob;
import com.nori.tc.db.domain.work.TcWorkControlJob;
import com.nori.tc.db.jpa.common.entity.work.TcWorkControlJobEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkControlJobEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkControlJobJpaRepository;

/**
 * tc_work_controljob JPA Store 구현체.
 *
 * <p>
 * 설계 전략:
 * <ul>
 *     <li><b>Upsert:</b> control_job_key가 있으면 PK 기준, 없으면 (work_key, controljob_id) 유니크 키로 조회 후 저장한다.</li>
 *     <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용한다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcWorkControlJobJpaStore implements TcWorkControlJobStore {

    private final TcWorkControlJobJpaRepository repository;
    private final TcWorkControlJobEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcWorkControlJobJpaStore(
            TcWorkControlJobJpaRepository repository,
            TcWorkControlJobEntityMapper mapper
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
    public TcWorkControlJob upsert(UpsertTcWorkControlJob command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            TcWorkControlJobEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcWorkControlJobEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_controljob] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlJobKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkControlJob> findByControlJobKey(long controlJobKey) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        try {
            return repository.findById(controlJobKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] findByControlJobKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param controljobId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkControlJob> findByWorkKeyAndControljobId(long workKey, String controljobId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (controljobId == null || controljobId.isBlank()) {
            throw new IllegalArgumentException("controljobId must not be null/blank");
        }
        try {
            return repository.findByWorkKeyAndControljobId(workKey, controljobId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] findByWorkKeyAndControljobId failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcWorkControlJob> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkControlJobEntity> cq = cb.createQuery(TcWorkControlJobEntity.class);
            Root<TcWorkControlJobEntity> root = cq.from(TcWorkControlJobEntity.class);

            Predicate predicate = cb.equal(root.get("workKey"), workKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("controlJobKey")));

            TypedQuery<TcWorkControlJobEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] findAllByWorkKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlJobKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByControlJobKey(long controlJobKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        try {
            repository.deleteById(controlJobKey);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_controljob] deleteByControlJobKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcWorkControlJob command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.controlJobKey() != null && command.controlJobKey() <= 0) {
            throw new IllegalArgumentException("command.controlJobKey must be > 0 when provided");
        }
        if (command.workKey() <= 0) {
            throw new IllegalArgumentException("command.workKey must be > 0");
        }
        if (command.controljobId() == null || command.controljobId().isBlank()) {
            throw new IllegalArgumentException("command.controljobId must not be null/blank");
        }
        if (command.controljobState() == null) {
            throw new IllegalArgumentException("command.controljobState must not be null");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private TcWorkControlJobEntity resolveEntity(UpsertTcWorkControlJob command) {
        if (command.controlJobKey() != null) {
            return repository.findById(command.controlJobKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_work_controljob] not found: controlJobKey=" + command.controlJobKey()
                    ));
        }

        return repository.findByWorkKeyAndControljobId(command.workKey(), command.controljobId())
                .orElseGet(() -> TcWorkControlJobEntity.newEntity(command.workKey(), command.controljobId()));
    }
}
