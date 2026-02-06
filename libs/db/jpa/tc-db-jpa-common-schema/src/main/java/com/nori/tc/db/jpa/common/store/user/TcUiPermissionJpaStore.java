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

    public TcUiPermissionJpaStore(EntityManager em, TcUiPermissionJpaRepository repository, TcUiPermissionEntityMapper mapper) {
        this.em = em;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUiPermission upsert(UpsertTcUiPermission command) {
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

    @Override
    @Transactional
    public void deleteByPermId(long permId) {
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
