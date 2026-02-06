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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.user.store.TcUserGroupMemberStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUserGroupMember;
import com.nori.tc.db.domain.user.TcUserGroupMember;
import com.nori.tc.db.jpa.common.entity.user.TcUserGroupMemberEntity;
import com.nori.tc.db.jpa.common.mapper.user.TcUserGroupMemberEntityMapper;
import com.nori.tc.db.jpa.common.repository.user.TcUserGroupMemberJpaRepository;

/**
 * tc_user_group_member JPA Store 구현체.
 *
 * <p>
 * 설계 전략:
 * <ul>
 *     <li><b>Upsert:</b> ugm_key가 있으면 PK 기준, 없으면 (user_pk, group_id) 유니크 키로 조회 후 저장한다.</li>
 *     <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용한다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcUserGroupMemberJpaStore implements TcUserGroupMemberStore {

    private final TcUserGroupMemberJpaRepository repository;
    private final TcUserGroupMemberEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcUserGroupMemberJpaStore(
            TcUserGroupMemberJpaRepository repository,
            TcUserGroupMemberEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUserGroupMember upsert(UpsertTcUserGroupMember command) {
        validateCommand(command);

        try {
            TcUserGroupMemberEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            if (command.grantedAt() != null) {
                entity.setGrantedAt(command.grantedAt());
            }

            TcUserGroupMemberEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_user_group_member] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_member] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroupMember> findByUgmKey(long ugmKey) {
        if (ugmKey <= 0) {
            throw new IllegalArgumentException("ugmKey must be > 0");
        }
        try {
            return repository.findById(ugmKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_member] findByUgmKey failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserGroupMember> findByUserPkAndGroupId(long userPk, long groupId) {
        validateUserPk(userPk);
        validateGroupId(groupId);
        try {
            return repository.findByUserPkAndGroupId(userPk, groupId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_member] findByUserPkAndGroupId failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroupMember> findAllByUserPk(long userPk, PageRequest page) {
        validateUserPk(userPk);
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcUserGroupMemberEntity> cq = cb.createQuery(TcUserGroupMemberEntity.class);
            Root<TcUserGroupMemberEntity> root = cq.from(TcUserGroupMemberEntity.class);

            Predicate predicate = cb.equal(root.get("userPk"), userPk);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("groupId")), cb.asc(root.get("ugmKey")));

            TypedQuery<TcUserGroupMemberEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_member] findAllByUserPk failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroupMember> findAllByGroupId(long groupId, PageRequest page) {
        validateGroupId(groupId);
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcUserGroupMemberEntity> cq = cb.createQuery(TcUserGroupMemberEntity.class);
            Root<TcUserGroupMemberEntity> root = cq.from(TcUserGroupMemberEntity.class);

            Predicate predicate = cb.equal(root.get("groupId"), groupId);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("userPk")), cb.asc(root.get("ugmKey")));

            TypedQuery<TcUserGroupMemberEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_member] findAllByGroupId failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByUgmKey(long ugmKey) {
        if (ugmKey <= 0) {
            throw new IllegalArgumentException("ugmKey must be > 0");
        }
        try {
            repository.deleteById(ugmKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_member] deleteByUgmKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByUserPkAndGroupId(long userPk, long groupId) {
        validateUserPk(userPk);
        validateGroupId(groupId);
        try {
            repository.deleteByUserPkAndGroupId(userPk, groupId);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_member] deleteByUserPkAndGroupId failed", e);
        }
    }

    private void validateCommand(UpsertTcUserGroupMember command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.ugmKey() != null && command.ugmKey() <= 0) {
            throw new IllegalArgumentException("command.ugmKey must be > 0 when provided");
        }
        validateUserPk(command.userPk());
        validateGroupId(command.groupId());
    }

    private void validateUserPk(long userPk) {
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be > 0");
        }
    }

    private void validateGroupId(long groupId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
    }

    private TcUserGroupMemberEntity resolveEntity(UpsertTcUserGroupMember command) {
        if (command.ugmKey() != null) {
            return repository.findById(command.ugmKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_user_group_member] not found: ugmKey=" + command.ugmKey()
                    ));
        }

        return repository.findByUserPkAndGroupId(command.userPk(), command.groupId())
                .orElseGet(() -> TcUserGroupMemberEntity.newEntity(command.userPk(), command.groupId()));
    }
}
