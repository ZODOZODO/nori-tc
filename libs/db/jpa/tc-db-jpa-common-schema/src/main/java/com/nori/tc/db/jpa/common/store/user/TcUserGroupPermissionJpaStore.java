package com.nori.tc.db.jpa.common.store.user;

import java.util.Collection;
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

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcUserGroupPermissionJpaStore(TcUserGroupPermissionJpaRepository repository, TcUserGroupPermissionEntityMapper mapper) {
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
    public TcUserGroupPermission upsert(UpsertTcUserGroupPermission command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     * @param permId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
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

    
    /**
     * group_id 목록 기준 전체 조회 (IN 절, 페이징 없음).
     *
     * <p>여러 그룹의 권한을 한 번에 조회할 때 사용한다.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroupPermission> findAllByGroupIdIn(Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        try {
            return repository.findByGroupIdIn(groupIds).stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_permission] findAllByGroupIdIn failed", e);
        }
    }

    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     * @param permId DB JPA 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByGroupIdPermId(long groupId, long permId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcUserGroupPermission command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.groupId() <= 0) throw new IllegalArgumentException("command.groupId must be > 0");
        if (command.permId() <= 0) throw new IllegalArgumentException("command.permId must be > 0");
    }
}
