package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.model.store.TcModelMdfStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.jpa.common.entity.model.TcModelMdfEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelMdfEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelMdfJpaRepository;

/**
 * {@code tc_model_mdf} JPA 저장소 구현입니다.
 *
 * <p>
 * 스키마 제약(UNIQUE: model_version_key)에 맞춰 조회/업서트 기준을
 * {@code modelVersionKey}로 통일합니다.
 * </p>
 */
@Repository
public class TcModelMdfJpaStore implements TcModelMdfStore {

    private final TcModelMdfJpaRepository repository;
    private final TcModelMdfEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    /**
     * 필수 의존성을 주입받습니다.
     */
    public TcModelMdfJpaStore(TcModelMdfJpaRepository repository, TcModelMdfEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * MDF를 upsert 합니다.
     */
    @Override
    @Transactional
    public TcModelMdf upsert(UpsertTcModelMdf command) {
        validateUpsert(command);

        try {
            TcModelMdfEntity entity = resolveEntity(command);
            mapper.updateFromUpsert(command, entity);
            TcModelMdfEntity saved = repository.save(entity);
            return mapper.toDomain(saved);
        } catch (DbEntityNotFoundException ex) {
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            throw new DbDuplicateKeyException("[tc_model_mdf] upsert failed: modelVersionKey unique violation", ex);
        } catch (RuntimeException ex) {
            throw new DbAccessException("[tc_model_mdf] upsert failed", ex);
        }
    }

    /**
     * {@code mdfKey} 기준으로 MDF를 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByMdfKey(long mdfKey) {
        if (mdfKey <= 0L) {
            throw new IllegalArgumentException("mdfKey must be > 0");
        }
        try {
            return repository.findById(mdfKey).map(mapper::toDomain);
        } catch (RuntimeException ex) {
            throw new DbAccessException("[tc_model_mdf] findByMdfKey failed: mdfKey=" + mdfKey, ex);
        }
    }

    /**
     * {@code modelVersionKey} 기준 단건 MDF를 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByModelVersionKey(long modelVersionKey) {
        if (modelVersionKey <= 0L) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        try {
            return repository.findByModelVersionKey(modelVersionKey).map(mapper::toDomain);
        } catch (RuntimeException ex) {
            throw new DbAccessException("[tc_model_mdf] findByModelVersionKey failed: modelVersionKey=" + modelVersionKey, ex);
        }
    }

    /**
     * {@code modelVersionKey} 기준 MDF 목록을 조회합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TcModelMdf> findAllByModelVersionKey(long modelVersionKey, PageRequest page) {
        if (modelVersionKey <= 0L) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }
        final PageRequest normalizedPage = page == null ? PageRequest.defaultPage() : page;

        try {
            TypedQuery<TcModelMdfEntity> query = em.createQuery(
                    "SELECT e FROM TcModelMdfEntity e WHERE e.modelVersionKey = :modelVersionKey ORDER BY e.mdfKey DESC",
                    TcModelMdfEntity.class
            );
            query.setParameter("modelVersionKey", modelVersionKey);
            query.setFirstResult(normalizedPage.offset());
            query.setMaxResults(normalizedPage.limit());
            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException ex) {
            throw new DbAccessException("[tc_model_mdf] findAllByModelVersionKey failed: modelVersionKey=" + modelVersionKey, ex);
        }
    }

    /**
     * {@code mdfKey} 기준으로 MDF를 삭제합니다.
     */
    @Override
    @Transactional
    public void deleteByMdfKey(long mdfKey) {
        if (mdfKey <= 0L) {
            throw new IllegalArgumentException("mdfKey must be > 0");
        }
        try {
            repository.deleteById(mdfKey);
        } catch (EmptyResultDataAccessException ignored) {
            // 멱등 삭제를 허용합니다.
        } catch (RuntimeException ex) {
            throw new DbAccessException("[tc_model_mdf] deleteByMdfKey failed: mdfKey=" + mdfKey, ex);
        }
    }

    /**
     * upsert 입력값을 검증합니다.
     */
    private void validateUpsert(UpsertTcModelMdf command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.mdfKey() != null && command.mdfKey() <= 0L) {
            throw new IllegalArgumentException("command.mdfKey must be > 0 when provided");
        }
        if (command.modelVersionKey() <= 0L) {
            throw new IllegalArgumentException("command.modelVersionKey must be > 0");
        }
        if (command.mdfName() == null || command.mdfName().isBlank()) {
            throw new IllegalArgumentException("command.mdfName must not be null/blank");
        }
        if (command.mdfFile() == null || command.mdfFile().length == 0) {
            throw new IllegalArgumentException("command.mdfFile must not be null/empty");
        }
    }

    /**
     * upsert 대상 엔티티를 결정합니다.
     *
     * <p>
     * 1) {@code mdfKey}가 있으면 PK로 조회합니다.
     * 2) 없으면 {@code modelVersionKey}로 조회합니다.
     * 3) 둘 다 없으면 신규 엔티티를 생성합니다.
     * </p>
     */
    private TcModelMdfEntity resolveEntity(UpsertTcModelMdf command) {
        if (command.mdfKey() != null) {
            return repository.findById(command.mdfKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_model_mdf] not found by mdfKey: " + command.mdfKey()
                    ));
        }

        return repository.findByModelVersionKey(command.modelVersionKey())
                .orElseGet(() -> TcModelMdfEntity.newEntity(
                        command.modelVersionKey(),
                        command.mdfName(),
                        command.mdfFile()
                ));
    }
}
