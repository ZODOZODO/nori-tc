package com.nori.tc.db.jpa.common.store.user;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import com.nori.tc.db.core.user.store.TcUserInfoStore;
import com.nori.tc.db.core.user.upsert.UpsertTcUserInfo;
import com.nori.tc.db.domain.user.TcUserInfo;
import com.nori.tc.db.jpa.common.entity.user.TcUserInfoEntity;
import com.nori.tc.db.jpa.common.mapper.user.TcUserInfoEntityMapper;
import com.nori.tc.db.jpa.common.repository.user.TcUserInfoJpaRepository;

/**
 * tc_user_info JPA Store 구현체.
 *
 * <p>
 * <b>최적화 포인트:</b>
 * <ul>
 * <li>MapStruct를 활용하여 Command -> Entity 변환 및 Dirty Checking을 자동화했습니다.</li>
 * <li>Criteria API를 사용하여 조건/페이징을 타입 안전하게 구현했습니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcUserInfoJpaStore implements TcUserInfoStore {

    private final TcUserInfoJpaRepository repository;
    private final TcUserInfoEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcUserInfoJpaStore(TcUserInfoJpaRepository repository, TcUserInfoEntityMapper mapper) {
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
    public TcUserInfo upsert(UpsertTcUserInfo command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        try {
            final Long userPk = command.userPk();
            final String userIdNorm = command.userIdNorm();

            // 1. 조회 또는 신규 생성
            final TcUserInfoEntity entity = resolveEntity(userPk, userIdNorm);

            // 2. [MapStruct] Command 값으로 Entity 업데이트 (Dirty Checking 유도)
            mapper.updateEntity(command, entity);

            // 2-1. 생성자 정보는 최초 생성 시점에만 반영 (null/blank 방어)
            if (entity.getUserPk() == null && command.createdBy() != null && !command.createdBy().isBlank()) {
                entity.setCreatedBy(command.createdBy());
            }

            // 3. 저장 및 반환
            TcUserInfoEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_user_info] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_info] upsert failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserInfo> findByUserPk(long userPk) {
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be positive");
        }
        try {
            return repository.findById(userPk).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_info] findByUserPk failed: userPk=" + userPk, e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userIdNorm DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserInfo> findByUserIdNorm(String userIdNorm) {
        if (userIdNorm == null || userIdNorm.isBlank()) {
            throw new IllegalArgumentException("userIdNorm must not be null/blank");
        }
        try {
            return repository.findByUserIdNorm(userIdNorm).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_info] findByUserIdNorm failed: userIdNorm=" + userIdNorm, e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param email DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcUserInfo> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be null/blank");
        }
        try {
            return repository.findByEmail(email).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_info] findByEmail failed: email=" + email, e);
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
    public List<TcUserInfo> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcUserInfoEntity> cq = cb.createQuery(TcUserInfoEntity.class);
            Root<TcUserInfoEntity> root = cq.from(TcUserInfoEntity.class);

            cq.select(root)
                    .orderBy(cb.desc(root.get("updatedAt")), cb.asc(root.get("userPk")));

            TypedQuery<TcUserInfoEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_info] findAll failed", e);
        }
    }

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param company DB JPA 계층 처리에 사용하는 입력 값
     * @param department DB JPA 계층 처리에 사용하는 입력 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcUserInfo> findAllByCompanyDepartment(String company, String department, PageRequest page) {
        if (company == null || company.isBlank()) {
            throw new IllegalArgumentException("company must not be null/blank");
        }
        if (department == null || department.isBlank()) {
            throw new IllegalArgumentException("department must not be null/blank");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcUserInfoEntity> cq = cb.createQuery(TcUserInfoEntity.class);
            Root<TcUserInfoEntity> root = cq.from(TcUserInfoEntity.class);

            cq.select(root)
                    .where(
                            cb.equal(root.get("company"), company),
                            cb.equal(root.get("department"), department)
                    )
                    .orderBy(cb.asc(root.get("userIdNorm")));

            TypedQuery<TcUserInfoEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_info] findAllByCompanyDepartment failed", e);
        }
    }

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB JPA 계층 처리에 사용하는 입력 값
     */
    @Override
    @Transactional
    public void deleteByUserPk(long userPk) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (userPk <= 0) {
            throw new IllegalArgumentException("userPk must be positive");
        }
        try {
            repository.deleteByUserPk(userPk);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_user_info] deleteByUserPk failed: userPk=" + userPk, e);
        }
    }

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB JPA 계층 처리에 사용하는 입력 값
     * @param userIdNorm DB JPA 계층 처리에 사용하는 입력 값
     * @return DB JPA 계층 처리 결과
     */
    private TcUserInfoEntity resolveEntity(Long userPk, String userIdNorm) {
        if (userPk != null && userPk > 0) {
            return repository.findById(userPk).orElseGet(() -> TcUserInfoEntity.newEntity(userIdNorm));
        }
        return repository.findByUserIdNorm(userIdNorm).orElseGet(() -> TcUserInfoEntity.newEntity(userIdNorm));
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
    private void validateCommand(UpsertTcUserInfo command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.company() == null || command.company().isBlank()) {
            throw new IllegalArgumentException("command.company must not be null/blank");
        }
        if (command.department() == null || command.department().isBlank()) {
            throw new IllegalArgumentException("command.department must not be null/blank");
        }
        if (command.userName() == null || command.userName().isBlank()) {
            throw new IllegalArgumentException("command.userName must not be null/blank");
        }
        if (command.userId() == null || command.userId().isBlank()) {
            throw new IllegalArgumentException("command.userId must not be null/blank");
        }
        if (command.userIdNorm() == null || command.userIdNorm().isBlank()) {
            throw new IllegalArgumentException("command.userIdNorm must not be null/blank");
        }
        if (command.passwordHash() == null || command.passwordHash().isBlank()) {
            throw new IllegalArgumentException("command.passwordHash must not be null/blank");
        }
        if (command.email() == null || command.email().isBlank()) {
            throw new IllegalArgumentException("command.email must not be null/blank");
        }
    }
}
