package com.nori.tc.db.jpa.common.store.eqp;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqp;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpJpaRepository;

/**
 * tc_eqp JPA Store 구현체.
 *
 * <p>
 * <b>최적화 포인트:</b>
 * <ul>
 * <li>MapStruct를 활용하여 Command -> Entity 변환 및 Dirty Checking을 자동화했습니다.</li>
 * <li>Criteria API를 사용하여 동적 쿼리 및 페이징을 타입 안전하게 구현했습니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcEqpJpaStore implements TcEqpStore {

    private static final Logger log = LoggerFactory.getLogger(TcEqpJpaStore.class);

    private final TcEqpJpaRepository repository;
    private final TcEqpEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpJpaStore(TcEqpJpaRepository repository, TcEqpEntityMapper mapper) {
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
    public TcEqp upsert(UpsertTcEqp command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        if (log.isDebugEnabled()) {
            log.debug("tc_eqp JPA upsert 시작. eqpId={}, commInterface={}, commMode={}, routePartition={}",
                    command.eqpId(),
                    command.commInterface(),
                    command.commMode(),
                    command.routePartition());
        }

        try {
            final String eqpId = command.eqpId();

            // 1. 조회 또는 신규 생성
            final TcEqpEntity entity = repository.findByEqpId(eqpId).orElseGet(() -> TcEqpEntity.newEntity(eqpId));

            // 2. [MapStruct] Command 값으로 Entity 업데이트 (Dirty Checking 유도)
            mapper.updateEntity(command, entity);

            // 2-1. 생성자 정보는 최초 생성 시점에만 반영 (null/blank 방어)
            if (entity.getEqpKey() == null && command.createdBy() != null && !command.createdBy().isBlank()) {
                entity.setCreatedBy(command.createdBy());
            }

            // 3. 저장 및 반환
            TcEqpEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqp> findByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            return repository.findByEqpId(eqpId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp] findByEqpId failed: eqpId=" + eqpId, e);
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
    public List<TcEqp> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcEqpEntity> cq = cb.createQuery(TcEqpEntity.class);
            Root<TcEqpEntity> root = cq.from(TcEqpEntity.class);

            cq.select(root);

            // --- 정렬 및 페이징 ---
            cq.orderBy(cb.asc(root.get("eqpId"))); // eqpId 오름차순 고정

            TypedQuery<TcEqpEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            // --- 변환 ---
            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp] findAll failed", e);
        }
    }

    /**
     * route_partition + enabled 조건으로 tc_eqp를 페이징 조회합니다.
     *
     * <p>Gateway 기동 시 owned partition 설비만 선별 로딩하기 위한 쿼리 계약의 JPA 구현입니다.</p>
     * <p>입력 routePartitions가 비어 있으면 DB를 조회하지 않고 즉시 빈 목록을 반환합니다.</p>
     *
     * @param routePartitions 조회 대상 route_partition 목록
     * @param enabled enabled 필터 값
     * @param page 페이징 조건 (null 허용)
     * @return 조건에 일치하는 tc_eqp 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcEqp> findAllByRoutePartitionsAndEnabled(List<Integer> routePartitions, boolean enabled, PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        if (routePartitions == null || routePartitions.isEmpty()) {
            log.info("tc_eqp route_partition 조건 JPA 조회를 건너뜁니다. 사유=빈 partition 목록, enabled={}", enabled);
            return List.of();
        }

        if (log.isDebugEnabled()) {
            log.debug("tc_eqp route_partition 조건 JPA 조회 시작. routePartitions={}, enabled={}, offset={}, limit={}",
                    routePartitions,
                    enabled,
                    p.offset(),
                    p.limit());
        }

        try {
            final CriteriaBuilder cb = em.getCriteriaBuilder();
            final CriteriaQuery<TcEqpEntity> cq = cb.createQuery(TcEqpEntity.class);
            final Root<TcEqpEntity> root = cq.from(TcEqpEntity.class);

            final Predicate routePartitionIn = root.get("routePartition").in(routePartitions);
            final Predicate enabledEquals = cb.equal(root.get("enabled"), enabled);

            cq.select(root)
              .where(cb.and(routePartitionIn, enabledEquals))
              .orderBy(cb.asc(root.get("eqpId")));

            final TypedQuery<TcEqpEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            final List<TcEqp> rows = query.getResultList().stream().map(mapper::toDomain).toList();

            if (log.isDebugEnabled()) {
                log.debug("tc_eqp route_partition 조건 JPA 조회 완료. resultCount={}", rows.size());
            }
            return rows;
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp] findAllByRoutePartitionsAndEnabled failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     */
    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            repository.deleteByEqpId(eqpId);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp] deleteByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqp command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpId() == null || command.eqpId().isBlank()) throw new IllegalArgumentException("command.eqpId must not be null/blank");
        if (command.commInterface() == null) throw new IllegalArgumentException("command.commInterface must not be null");
        if (command.commMode() == null || command.commMode().isBlank()) throw new IllegalArgumentException("command.commMode must not be null/blank");
        if (!"ACTIVE".equals(command.commMode()) && !"PASSIVE".equals(command.commMode())) {
            throw new IllegalArgumentException("command.commMode must be ACTIVE or PASSIVE");
        }
        if (command.routePartition() != null && command.routePartition() < 0) {
            throw new IllegalArgumentException("command.routePartition must be >= 0 when present");
        }
        if (command.eqpIp() == null || command.eqpIp().isBlank()) throw new IllegalArgumentException("command.eqpIp must not be null/blank");
        if (command.eqpPort() <= 0) throw new IllegalArgumentException("command.eqpPort must be > 0");
        if (command.modelVersionKey() <= 0) throw new IllegalArgumentException("command.modelVersionKey must be > 0");
    }
}
