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
import com.nori.tc.db.core.user.store.TcUserGroupStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUserGroup;
import com.nori.tc.db.domain.user.TcUserGroup;
import com.nori.tc.db.jpa.common.entity.user.TcUserGroupEntity;
import com.nori.tc.db.jpa.common.mapper.user.TcUserGroupEntityMapper;
import com.nori.tc.db.jpa.common.repository.user.TcUserGroupJpaRepository;

/**
 * tc_user_group JPA Store 구현체.
 */
@Repository
public class TcUserGroupJpaStore implements TcUserGroupStore {

    private final EntityManager em;
    private final TcUserGroupJpaRepository repository;
    private final TcUserGroupEntityMapper mapper;

    public TcUserGroupJpaStore(EntityManager em, TcUserGroupJpaRepository repository, TcUserGroupEntityMapper mapper) {
        this.em = em;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUserGroup upsert(UpsertTcUserGroup command) {
        if (command == null) throw new IllegalArgumentException("UpsertTcUserGroup must not be null");
        if (command.groupCode() == null || command.groupCode().isBlank()) {
            throw new IllegalArgumentException("groupCode must not be null/blank");
        }
        if (command.groupName() == null || command.groupName().isBlank()) {
            throw new IllegalArgumentException("groupName must not be null/blank");
        }

        try {
            TcUserGroupEntity entity = resolveEntity(command);
            mapper.updateFromUpsert(command, entity);
            entity.setIsActive(command.isActive());
            TcUserGroupEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_user_group] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroup> findByGroupId(long groupId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be positive");
        }
        try {
            return repository.findById(groupId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group] findByGroupId failed: groupId=" + groupId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroup> findByGroupCode(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw new IllegalArgumentException("groupCode must not be null/blank");
        }
        try {
            return repository.findByGroupCode(groupCode).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group] findByGroupCode failed: groupCode=" + groupCode, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroup> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcUserGroupEntity> cq = cb.createQuery(TcUserGroupEntity.class);
            Root<TcUserGroupEntity> root = cq.from(TcUserGroupEntity.class);

            cq.select(root);
            cq.orderBy(cb.desc(root.get("updatedAt")), cb.asc(root.get("groupCode")));

            TypedQuery<TcUserGroupEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group] findAll failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByGroupId(long groupId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be positive");
        }
        try {
            repository.deleteById(groupId);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group] deleteByGroupId failed", e);
        }
    }

    private TcUserGroupEntity resolveEntity(UpsertTcUserGroup command) {
        Long groupId = command.groupId();
        if (groupId != null && groupId > 0) {
            return repository.findById(groupId)
                    .orElseGet(() -> TcUserGroupEntity.newEntity(command.groupCode(), command.groupName()));
        }
        return repository.findByGroupCode(command.groupCode())
                .orElseGet(() -> TcUserGroupEntity.newEntity(command.groupCode(), command.groupName()));
    }
}
