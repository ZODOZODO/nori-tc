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

    public TcUserInfoJpaStore(TcUserInfoJpaRepository repository, TcUserInfoEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcUserInfo upsert(UpsertTcUserInfo command) {
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

    @Override
    @Transactional
    public void deleteByUserPk(long userPk) {
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

    private TcUserInfoEntity resolveEntity(Long userPk, String userIdNorm) {
        if (userPk != null && userPk > 0) {
            return repository.findById(userPk).orElseGet(() -> TcUserInfoEntity.newEntity(userIdNorm));
        }
        return repository.findByUserIdNorm(userIdNorm).orElseGet(() -> TcUserInfoEntity.newEntity(userIdNorm));
    }

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
