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

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcUserGroupMemberJpaStore(
            TcUserGroupMemberJpaRepository repository,
            TcUserGroupMemberEntityMapper mapper
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
    public TcUserGroupMember upsert(UpsertTcUserGroupMember command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param ugmKey 대상 키 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB JPA 계층 처리에 사용하는 입력 값
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * user_pk 기준 전체 목록 조회 (페이징 없음).
     *
     * <p>권한 조회처럼 결과 크기가 작고 전체를 한 번에 필요로 하는 경우에 사용한다.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcUserGroupMember> findAllByUserPk(long userPk) {
        validateUserPk(userPk);

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcUserGroupMemberEntity> cq = cb.createQuery(TcUserGroupMemberEntity.class);
            Root<TcUserGroupMemberEntity> root = cq.from(TcUserGroupMemberEntity.class);

            cq.select(root).where(cb.equal(root.get("userPk"), userPk));
            cq.orderBy(cb.asc(root.get("groupId")), cb.asc(root.get("ugmKey")));

            return em.createQuery(cq).getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_member] findAllByUserPk (no-page) failed", e);
        }
    }

    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param ugmKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByUgmKey(long ugmKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB JPA 계층 처리에 사용하는 입력 값
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByUserPkAndGroupId(long userPk, long groupId) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateUserPk(userPk);
        validateGroupId(groupId);
        try {
            repository.deleteByUserPkAndGroupId(userPk, groupId);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_group_member] deleteByUserPkAndGroupId failed", e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
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

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB JPA 계층 처리에 사용하는 입력 값
     */
    private void validateUserPk(long userPk) {
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be > 0");
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     */
    private void validateGroupId(long groupId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be > 0");
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
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
