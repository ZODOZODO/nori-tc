package com.nori.tc.db.jpa.common.store.user;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
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
import com.nori.tc.db.core.user.store.TcUiPermissionStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUiPermission;
import com.nori.tc.db.domain.user.TcUiPermission;
import com.nori.tc.db.jpa.common.entity.user.TcUiPermissionEntity;
import com.nori.tc.db.jpa.common.mapper.user.TcUiPermissionEntityMapper;
import com.nori.tc.db.jpa.common.repository.user.TcUiPermissionJpaRepository;

/**
 * tc_ui_permission JPA Store 구현체.
 *
 * <p>
 * <b>운영 포인트:</b>
 * <ul>
 * <li>perm_code UNIQUE 키를 기준으로 upsert를 수행합니다.</li>
 * <li>Criteria API를 사용하여 페이징/정렬을 안전하게 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcUiPermissionJpaStore implements TcUiPermissionStore {

    private final EntityManager em;
    private final TcUiPermissionJpaRepository repository;
    private final TcUiPermissionEntityMapper mapper;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param em DB JPA 계층 처리에 사용하는 입력 값
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcUiPermissionJpaStore(EntityManager em, TcUiPermissionJpaRepository repository, TcUiPermissionEntityMapper mapper) {
        this.em = em;
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
    public TcUiPermission upsert(UpsertTcUiPermission command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            TcUiPermissionEntity entity = resolveEntity(command);
            mapper.updateFromUpsert(command, entity);

            // createdBy는 최초 생성 시점에만 반영 (null/blank 방어)
            if (entity.getPermId() == null && command.createdBy() != null && !command.createdBy().isBlank()) {
                entity.setCreatedBy(command.createdBy());
            }

            TcUiPermissionEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_ui_permission] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_permission] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUiPermission> findByPermId(long permId) {
        if (permId <= 0) {
            throw new IllegalArgumentException("permId must be positive");
        }
        try {
            return repository.findById(permId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_permission] findByPermId failed: permId=" + permId, e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permCode DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUiPermission> findByPermCode(String permCode) {
        if (permCode == null || permCode.isBlank()) {
            throw new IllegalArgumentException("permCode must not be null/blank");
        }
        try {
            return repository.findByPermCode(permCode).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_permission] findByPermCode failed: permCode=" + permCode, e);
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
    public List<TcUiPermission> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcUiPermissionEntity> cq = cb.createQuery(TcUiPermissionEntity.class);
            Root<TcUiPermissionEntity> root = cq.from(TcUiPermissionEntity.class);

            cq.select(root);
            cq.orderBy(cb.desc(root.get("permId")));

            TypedQuery<TcUiPermissionEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_permission] findAll failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permId DB JPA 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByPermId(long permId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (permId <= 0) {
            throw new IllegalArgumentException("permId must be positive");
        }
        try {
            repository.deleteById(permId);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_ui_permission] deleteByPermId failed: permId=" + permId, e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcUiPermission command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.permCode() == null || command.permCode().isBlank()) {
            throw new IllegalArgumentException("command.permCode must not be null/blank");
        }
        if (command.permName() == null || command.permName().isBlank()) {
            throw new IllegalArgumentException("command.permName must not be null/blank");
        }
        if (command.resourceType() == null) {
            throw new IllegalArgumentException("command.resourceType must not be null");
        }
        if (command.resource() == null || command.resource().isBlank()) {
            throw new IllegalArgumentException("command.resource must not be null/blank");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    private TcUiPermissionEntity resolveEntity(UpsertTcUiPermission command) {
        Long permId = command.permId();
        if (permId != null && permId > 0) {
            return repository.findById(permId)
                    .orElseGet(() -> TcUiPermissionEntity.newEntity(command.permCode()));
        }
        return repository.findByPermCode(command.permCode())
                .orElseGet(() -> TcUiPermissionEntity.newEntity(command.permCode()));
    }
}
