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
import com.nori.tc.db.core.work.store.TcWorkProcessJobStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessJob;
import com.nori.tc.db.domain.work.TcWorkProcessJob;
import com.nori.tc.db.jpa.common.entity.work.TcWorkProcessJobEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkProcessJobEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkProcessJobJpaRepository;

/**
 * tc_work_processjob JPA Store 구현체.
 *
 * <p>
 * 설계 전략:
 * <ul>
 *     <li><b>Upsert:</b> process_job_key가 있으면 PK 기준, 없으면 (control_job_key, processjob_id) 유니크 키로 조회 후 저장한다.</li>
 *     <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용한다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcWorkProcessJobJpaStore implements TcWorkProcessJobStore {

    private final TcWorkProcessJobJpaRepository repository;
    private final TcWorkProcessJobEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcWorkProcessJobJpaStore(
            TcWorkProcessJobJpaRepository repository,
            TcWorkProcessJobEntityMapper mapper
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
    public TcWorkProcessJob upsert(UpsertTcWorkProcessJob command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            TcWorkProcessJobEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcWorkProcessJobEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_processjob] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessJob> findByProcessJobKey(long processJobKey) {
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        try {
            return repository.findById(processJobKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] findByProcessJobKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlJobKey 대상 키 값
     * @param processjobId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkProcessJob> findByControlJobKeyAndProcessjobId(long controlJobKey, String processjobId) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        if (processjobId == null || processjobId.isBlank()) {
            throw new IllegalArgumentException("processjobId must not be null/blank");
        }
        try {
            return repository.findByControlJobKeyAndProcessjobId(controlJobKey, processjobId)
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] findByControlJobKeyAndProcessjobId failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param controlJobKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcWorkProcessJob> findAllByControlJobKey(long controlJobKey, PageRequest page) {
        if (controlJobKey <= 0) {
            throw new IllegalArgumentException("controlJobKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkProcessJobEntity> cq = cb.createQuery(TcWorkProcessJobEntity.class);
            Root<TcWorkProcessJobEntity> root = cq.from(TcWorkProcessJobEntity.class);

            Predicate predicate = cb.equal(root.get("controlJobKey"), controlJobKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.desc(root.get("processJobKey")));

            TypedQuery<TcWorkProcessJobEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] findAllByControlJobKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param processJobKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByProcessJobKey(long processJobKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (processJobKey <= 0) {
            throw new IllegalArgumentException("processJobKey must be > 0");
        }
        try {
            repository.deleteById(processJobKey);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_processjob] deleteByProcessJobKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcWorkProcessJob command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.processJobKey() != null && command.processJobKey() <= 0) {
            throw new IllegalArgumentException("command.processJobKey must be > 0 when provided");
        }
        if (command.controlJobKey() <= 0) {
            throw new IllegalArgumentException("command.controlJobKey must be > 0");
        }
        if (command.processjobId() == null || command.processjobId().isBlank()) {
            throw new IllegalArgumentException("command.processjobId must not be null/blank");
        }
        if (command.processjobState() == null) {
            throw new IllegalArgumentException("command.processjobState must not be null");
        }
        if (command.recipeId() == null || command.recipeId().isBlank()) {
            throw new IllegalArgumentException("command.recipeId must not be null/blank");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private TcWorkProcessJobEntity resolveEntity(UpsertTcWorkProcessJob command) {
        if (command.processJobKey() != null) {
            return repository.findById(command.processJobKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_work_processjob] not found: processJobKey=" + command.processJobKey()
                    ));
        }

        return repository.findByControlJobKeyAndProcessjobId(command.controlJobKey(), command.processjobId())
                .orElseGet(() -> TcWorkProcessJobEntity.newEntity(command.controlJobKey(), command.processjobId()));
    }
}
