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
import com.nori.tc.db.core.work.store.TcWorkCarrierStore;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrier;
import com.nori.tc.db.domain.work.TcWorkCarrier;
import com.nori.tc.db.jpa.common.entity.work.TcWorkCarrierEntity;
import com.nori.tc.db.jpa.common.mapper.work.TcWorkCarrierEntityMapper;
import com.nori.tc.db.jpa.common.repository.work.TcWorkCarrierJpaRepository;

/**
 * tc_work_carrier JPA Store 구현체.
 *
 * <p>
 * <b>계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> (work_key, carrier_id) 유니크 키로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcWorkCarrierJpaStore implements TcWorkCarrierStore {

    private final TcWorkCarrierJpaRepository repository;
    private final TcWorkCarrierEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcWorkCarrierJpaStore(TcWorkCarrierJpaRepository repository, TcWorkCarrierEntityMapper mapper) {
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
    public TcWorkCarrier upsert(UpsertTcWorkCarrier command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            final long workKey = command.workKey();
            final String carrierId = command.carrierId();

            TcWorkCarrierEntity entity = repository.findByWorkKeyAndCarrierId(workKey, carrierId)
                    .orElseGet(() -> TcWorkCarrierEntity.newEntity(workKey, carrierId));

            mapper.updateEntity(command, entity);

            TcWorkCarrierEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_work_carrier] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param carrierId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcWorkCarrier> findByWorkKeyCarrierId(long workKey, String carrierId) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (carrierId == null || carrierId.isBlank()) {
            throw new IllegalArgumentException("carrierId must not be null/blank");
        }
        try {
            return repository.findByWorkKeyAndCarrierId(workKey, carrierId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier] findByWorkKeyCarrierId failed", e);
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
    public List<TcWorkCarrier> findAllByWorkKey(long workKey, PageRequest page) {
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcWorkCarrierEntity> cq = cb.createQuery(TcWorkCarrierEntity.class);
            Root<TcWorkCarrierEntity> root = cq.from(TcWorkCarrierEntity.class);

            Predicate predicate = cb.equal(root.get("workKey"), workKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("carrierId")));

            TypedQuery<TcWorkCarrierEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier] findAllByWorkKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param carrierId DB JPA 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByWorkKeyCarrierId(long workKey, String carrierId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (workKey <= 0) {
            throw new IllegalArgumentException("workKey must be > 0");
        }
        if (carrierId == null || carrierId.isBlank()) {
            throw new IllegalArgumentException("carrierId must not be null/blank");
        }
        try {
            repository.findByWorkKeyAndCarrierId(workKey, carrierId).ifPresent(repository::delete);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_work_carrier] deleteByWorkKeyCarrierId failed", e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcWorkCarrier command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.workKey() <= 0) throw new IllegalArgumentException("command.workKey must be > 0");
        if (command.carrierId() == null || command.carrierId().isBlank()) throw new IllegalArgumentException("command.carrierId must not be null/blank");
    }
}
