package com.nori.tc.db.jpa.common.store.user;

import java.time.OffsetDateTime;
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
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.user.store.TcUiAuthSessionStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUiAuthSession;
import com.nori.tc.db.domain.user.TcUiAuthSession;
import com.nori.tc.db.jpa.common.entity.user.TcUiAuthSessionEntity;
import com.nori.tc.db.jpa.common.mapper.user.TcUiAuthSessionEntityMapper;
import com.nori.tc.db.jpa.common.repository.user.TcUiAuthSessionJpaRepository;

/**
 * tc_ui_auth_session JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> token으로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcUiAuthSessionJpaStore implements TcUiAuthSessionStore {

    private final TcUiAuthSessionJpaRepository repository;
    private final TcUiAuthSessionEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcUiAuthSessionJpaStore(
            TcUiAuthSessionJpaRepository repository,
            TcUiAuthSessionEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUiAuthSession upsert(UpsertTcUiAuthSession command) {
        UpsertTcUiAuthSession normalized = normalizeCommand(command);
        validateCommand(normalized);

        try {
            final String token = normalized.token();
            final long userPk = normalized.userPk();

            TcUiAuthSessionEntity entity = repository.findById(token)
                    .orElseGet(() -> TcUiAuthSessionEntity.newEntity(token, userPk));

            mapper.updateEntity(normalized, entity);

            TcUiAuthSessionEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_ui_auth_session] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_auth_session] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUiAuthSession> findByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null/blank");
        }
        try {
            return repository.findById(token).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_auth_session] findByToken failed: token=" + token, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUiAuthSession> findAllByUserPk(long userPk, PageRequest page) {
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be positive");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcUiAuthSessionEntity> cq = cb.createQuery(TcUiAuthSessionEntity.class);
            Root<TcUiAuthSessionEntity> root = cq.from(TcUiAuthSessionEntity.class);

            Predicate predicate = cb.equal(root.get("userPk"), userPk);
            cq.select(root).where(predicate);
            cq.orderBy(cb.desc(root.get("issuedAt")), cb.asc(root.get("token")));

            TypedQuery<TcUiAuthSessionEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_auth_session] findAllByUserPk failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null/blank");
        }
        try {
            repository.deleteById(token);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_auth_session] deleteByToken failed: token=" + token, e);
        }
    }

    private void validateCommand(UpsertTcUiAuthSession command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.token() == null || command.token().isBlank()) {
            throw new IllegalArgumentException("command.token must not be null/blank");
        }
        if (command.token().length() > 64) {
            throw new IllegalArgumentException("command.token length must be <= 64");
        }
        if (command.userPk() == null || command.userPk() <= 0) {
            throw new IllegalArgumentException("command.userPk must be positive");
        }
        if (command.issuedAt() == null) {
            throw new IllegalArgumentException("command.issuedAt must not be null");
        }
        if (command.expiresAt() == null) {
            throw new IllegalArgumentException("command.expiresAt must not be null");
        }
        if (command.revoked() == null) {
            throw new IllegalArgumentException("command.revoked must not be null");
        }
    }

    private UpsertTcUiAuthSession normalizeCommand(UpsertTcUiAuthSession command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcUiAuthSession(
                command.token(),
                command.userPk(),
                command.issuedAt() == null ? OffsetDateTime.now() : command.issuedAt(),
                command.expiresAt(),
                command.lastSeenAt(),
                command.revoked() == null ? Boolean.FALSE : command.revoked()
        );
    }
}
