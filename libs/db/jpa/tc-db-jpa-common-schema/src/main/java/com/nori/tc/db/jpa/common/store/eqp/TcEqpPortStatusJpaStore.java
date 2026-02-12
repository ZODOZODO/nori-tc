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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpPortStatusStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpPortStatus;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpPortStatus;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpPortStatusEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpPortStatusEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpPortStatusJpaRepository;

/**
 * tc_eqp_port_status JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> (eqp_key, port_id) 유니크 키로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcEqpPortStatusJpaStore implements TcEqpPortStatusStore {

    private final TcEqpPortStatusJpaRepository repository;
    private final TcEqpPortStatusEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpPortStatusJpaStore(TcEqpPortStatusJpaRepository repository, TcEqpPortStatusEntityMapper mapper) {
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
    public TcEqpPortStatus upsert(UpsertTcEqpPortStatus command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            final long eqpKey = command.eqpKey();
            final String portId = command.portId();

            TcEqpPortStatusEntity entity = repository.findByEqpKeyAndPortId(eqpKey, portId)
                    .orElseGet(() -> TcEqpPortStatusEntity.newEntity(eqpKey, portId));

            mapper.updateEntity(command, entity);

            TcEqpPortStatusEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_port_status] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_port_status] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param portId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpPortStatus> findByEqpKeyPortId(long eqpKey, String portId) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (portId == null || portId.isBlank()) {
            throw new IllegalArgumentException("portId must not be null/blank");
        }
        try {
            return repository.findByEqpKeyAndPortId(eqpKey, portId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_port_status] findByEqpKeyPortId failed", e);
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
    public List<TcEqpPortStatus> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcEqpPortStatusEntity> cq = cb.createQuery(TcEqpPortStatusEntity.class);
            Root<TcEqpPortStatusEntity> root = cq.from(TcEqpPortStatusEntity.class);

            Predicate predicate = cb.equal(root.get("eqpKey"), eqpKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("portId")));

            TypedQuery<TcEqpPortStatusEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_port_status] findAllByEqpKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param portId DB JPA 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByEqpKeyPortId(long eqpKey, String portId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (portId == null || portId.isBlank()) {
            throw new IllegalArgumentException("portId must not be null/blank");
        }
        try {
            repository.findByEqpKeyAndPortId(eqpKey, portId).ifPresent(repository::delete);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_port_status] deleteByEqpKeyPortId failed", e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpPortStatus command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be > 0");
        if (command.portId() == null || command.portId().isBlank()) throw new IllegalArgumentException("command.portId must not be null/blank");
    }
}
