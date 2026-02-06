package com.nori.tc.db.jpa.common.store.user;

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
import com.nori.tc.db.core.user.store.TcUserGroupPermissionStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUserGroupPermission;
import com.nori.tc.db.domain.user.TcUserGroupPermission;
import com.nori.tc.db.jpa.common.entity.user.TcUserGroupPermissionEntity;
import com.nori.tc.db.jpa.common.mapper.user.TcUserGroupPermissionEntityMapper;
import com.nori.tc.db.jpa.common.repository.user.TcUserGroupPermissionJpaRepository;

/**
 * tc_user_group_permission JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> (group_id, perm_id) 유니크 키로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcUserGroupPermissionJpaStore implements TcUserGroupPermissionStore {

    private final TcUserGroupPermissionJpaRepository repository;
    private final TcUserGroupPermissionEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcUserGroupPermissionJpaStore(TcUserGroupPermissionJpaRepository repository, TcUserGroupPermissionEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUserGroupPermission upsert(UpsertTcUserGroupPermission command) {
        validateCommand(command);

        try {
            final long groupId = command.groupId();
            final long permId = command.permId();

            TcUserGroupPermissionEntity entity = repository.findByGroupIdAndPermId(groupId, permId)
                    .orElseGet(() -> TcUserGroupPermissionEntity.newEntity(groupId, permId));

            mapper.updateEntity(command, entity);

            TcUserGroupPermissionEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_user_group_permission] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_permission] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroupPermission> findByGroupIdPermId(long groupId, long permId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
        if (permId <= 0) {
            throw new IllegalArgumentException("permId must be > 0");
        }
        try {
            return repository.findByGroupIdAndPermId(groupId, permId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_permission] findByGroupIdPermId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroupPermission> findAllByGroupId(long groupId, PageRequest page) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcUserGroupPermissionEntity> cq = cb.createQuery(TcUserGroupPermissionEntity.class);
            Root<TcUserGroupPermissionEntity> root = cq.from(TcUserGroupPermissionEntity.class);

            Predicate predicate = cb.equal(root.get("groupId"), groupId);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("permId")));

            TypedQuery<TcUserGroupPermissionEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_permission] findAllByGroupId failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByGroupIdPermId(long groupId, long permId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
        if (permId <= 0) {
            throw new IllegalArgumentException("permId must be > 0");
        }
        try {
            repository.findByGroupIdAndPermId(groupId, permId).ifPresent(repository::delete);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_permission] deleteByGroupIdPermId failed", e);
        }
    }

    private void validateCommand(UpsertTcUserGroupPermission command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.groupId() <= 0) throw new IllegalArgumentException("command.groupId must be > 0");
        if (command.permId() <= 0) throw new IllegalArgumentException("command.permId must be > 0");
    }
}
