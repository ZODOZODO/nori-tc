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

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param em DB JPA 계층 처리에 사용하는 입력 값
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcUserGroupJpaStore(EntityManager em, TcUserGroupJpaRepository repository, TcUserGroupEntityMapper mapper) {
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
    public TcUserGroup upsert(UpsertTcUserGroup command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupCode DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
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

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByGroupId(long groupId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
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
