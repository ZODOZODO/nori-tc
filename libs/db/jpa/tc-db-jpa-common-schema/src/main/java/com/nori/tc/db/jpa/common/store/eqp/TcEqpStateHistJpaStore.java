package com.nori.tc.db.jpa.common.store.eqp;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStateHistStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpStateHist;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpStateHist;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpStateHistEntity;
import com.nori.tc.db.jpa.common.mapper.eqp.TcEqpStateHistEntityMapper;
import com.nori.tc.db.jpa.common.repository.eqp.TcEqpStateHistJpaRepository;

/**
 * tc_eqp_state_hist JPA Store 구현체.
 */
@Repository
public class TcEqpStateHistJpaStore implements TcEqpStateHistStore {

    private final TcEqpStateHistJpaRepository repository;
    private final TcEqpStateHistEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcEqpStateHistJpaStore(TcEqpStateHistJpaRepository repository, TcEqpStateHistEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    @Override
    @Transactional
    public void append(UpsertTcEqpStateHist command) {
        UpsertTcEqpStateHist normalized = normalizeCommand(command);
        validateCommand(normalized);

        try {
            TcEqpStateHistEntity entity = TcEqpStateHistEntity.newEntity(normalized.eqpKey(), normalized.stateType());
            mapper.updateEntity(normalized, entity);
            repository.save(entity);
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_state_hist] append failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state_hist] append failed", e);
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
    public List<TcEqpStateHist> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcEqpStateHistEntity> cq = cb.createQuery(TcEqpStateHistEntity.class);
            Root<TcEqpStateHistEntity> root = cq.from(TcEqpStateHistEntity.class);

            cq.select(root)
                    .where(cb.equal(root.get("eqpKey"), eqpKey))
                    .orderBy(cb.desc(root.get("changedAt")), cb.desc(root.get("stateHistKey")));

            TypedQuery<TcEqpStateHistEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_state_hist] findAllByEqpKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcEqpStateHist command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() == null || command.eqpKey() <= 0) {
            throw new IllegalArgumentException("command.eqpKey must be positive");
        }
        if (command.stateType() == null) {
            throw new IllegalArgumentException("command.stateType must not be null");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private UpsertTcEqpStateHist normalizeCommand(UpsertTcEqpStateHist command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcEqpStateHist(
                command.eqpKey(),
                command.stateType(),
                command.fromState(),
                command.toState(),
                command.changedAt() == null ? OffsetDateTime.now() : command.changedAt(),
                command.reasonCode(),
                command.reasonDetail()
        );
    }
}
