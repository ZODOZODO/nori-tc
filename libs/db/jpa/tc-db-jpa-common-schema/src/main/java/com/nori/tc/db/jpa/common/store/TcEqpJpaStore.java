package com.nori.tc.db.jpa.common.store;

import java.util.ArrayList;
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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.TcEqpSearchCriteria;
import com.nori.tc.db.core.eqp.TcEqpStore;
import com.nori.tc.db.core.eqp.UpsertTcEqp;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.jpa.common.entity.TcEqpEntity;
import com.nori.tc.db.jpa.common.mapper.TcEqpEntityMapper;
import com.nori.tc.db.jpa.common.repository.TcEqpJpaRepository;

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

    private final TcEqpJpaRepository repository;
    private final TcEqpEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcEqpJpaStore(TcEqpJpaRepository repository, TcEqpEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqp upsert(UpsertTcEqp command) {
        validateCommand(command);

        try {
            final String eqpId = command.eqpId();

            // 1. 조회 또는 신규 생성
            final TcEqpEntity entity = repository.findById(eqpId).orElseGet(() -> TcEqpEntity.newEntity(eqpId));

            // 2. [MapStruct] Command 값으로 Entity 업데이트 (Dirty Checking 유도)
            mapper.updateEntity(command, entity);

            // 3. 저장 및 반환
            TcEqpEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqp> findByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            return repository.findById(eqpId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp] findByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqp> findAll(TcEqpSearchCriteria criteria, PageRequest page) {
        final TcEqpSearchCriteria c = (criteria == null) ? TcEqpSearchCriteria.empty() : criteria;
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcEqpEntity> cq = cb.createQuery(TcEqpEntity.class);
            Root<TcEqpEntity> root = cq.from(TcEqpEntity.class);

            List<Predicate> predicates = new ArrayList<>();

            // --- 동적 쿼리 조건 구성 ---
            if (c.protocolType() != null) {
                predicates.add(cb.equal(root.get("protocolType"), c.protocolType()));
            }
            if (c.enabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), c.enabled()));
            }

            cq.select(root);
            if (!predicates.isEmpty()) {
                cq.where(predicates.toArray(Predicate[]::new));
            }

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

    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            repository.deleteById(eqpId);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp] deleteByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    private void validateCommand(UpsertTcEqp command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpId() == null || command.eqpId().isBlank()) throw new IllegalArgumentException("command.eqpId must not be null/blank");
        if (command.protocolType() == null) throw new IllegalArgumentException("command.protocolType must not be null");
        if (command.eqpIp() == null || command.eqpIp().isBlank()) throw new IllegalArgumentException("command.eqpIp must not be null/blank");
        if (command.eqpPort() <= 0) throw new IllegalArgumentException("command.eqpPort must be > 0");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
    }
}