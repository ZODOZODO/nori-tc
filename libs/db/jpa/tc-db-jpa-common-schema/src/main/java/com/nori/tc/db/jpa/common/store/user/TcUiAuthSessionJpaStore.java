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

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcUiAuthSessionJpaStore(
            TcUiAuthSessionJpaRepository repository,
            TcUiAuthSessionEntityMapper mapper
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
    public TcUiAuthSession upsert(UpsertTcUiAuthSession command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param token DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB JPA 계층 처리에 사용하는 입력 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
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

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param token DB JPA 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByToken(String token) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    /**
     * 유효한 세션을 토큰으로 조회합니다 (revoked=false + expiresAt > 현재 시각).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUiAuthSession> findValidByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null/blank");
        }
        try {
            return repository.findByTokenAndRevokedFalseAndExpiresAtAfter(token, OffsetDateTime.now())
                    .map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_auth_session] findValidByToken failed: token=" + token, e);
        }
    }

    /**
     * 세션을 폐기합니다 (revoked = true). SELECT 없이 UPDATE 단일 쿼리를 실행합니다.
     */
    @Override
    @Transactional
    public void revokeByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null/blank");
        }
        try {
            repository.revokeByToken(token);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_auth_session] revokeByToken failed: token=" + token, e);
        }
    }

    /**
     * 마지막 접근 시각을 업데이트합니다. SELECT 없이 UPDATE 단일 쿼리를 실행합니다.
     */
    @Override
    @Transactional
    public void updateLastSeenAt(String token, OffsetDateTime lastSeenAt) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null/blank");
        }
        if (lastSeenAt == null) {
            throw new IllegalArgumentException("lastSeenAt must not be null");
        }
        try {
            repository.updateLastSeenAt(token, lastSeenAt);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_auth_session] updateLastSeenAt failed: token=" + token, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
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

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
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
