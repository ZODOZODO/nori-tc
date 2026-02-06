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
 * tc_model_mdf JPA Store 구현체.
 *
 * <p>
 * <b>주요 기능:</b>
 * <ul>
 * <li><b>Upsert:</b> MDF 키 또는 유니크 키로 존재 여부를 확인한 뒤 생성/갱신을 수행합니다.</li>
 * <li><b>목록 조회:</b> 특정 model_key 기준으로 최신 mdf_key DESC 정렬 + 페이징을 제공합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelMdfJpaStore implements TcModelMdfStore {

    private final TcModelMdfJpaRepository repository;
    private final TcModelMdfEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcModelMdfJpaStore(TcModelMdfJpaRepository repository, TcModelMdfEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelMdf upsert(UpsertTcModelMdf command) {
        validateUpsert(command);

        try {
            // 1. PK 또는 유니크 키 기반으로 대상 엔티티 결정
            TcModelMdfEntity entity = resolveEntity(command);

            // 2. Dirty Checking용 필드 업데이트
            mapper.updateFromUpsert(command, entity);

            // 3. 저장 및 반환
            TcModelMdfEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DbEntityNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_mdf] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByMdfKey(long mdfKey) {
        if (mdfKey <= 0) {
            throw new IllegalArgumentException("mdfKey must be > 0");
        }
        try {
            return repository.findById(mdfKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] findByMdfKey failed: mdfKey=" + mdfKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelMdf> findByModelKeyAndName(long modelKey, String mdfName) {
        if (modelKey <= 0) throw new IllegalArgumentException("modelKey must be > 0");
        if (mdfName == null || mdfName.isBlank()) throw new IllegalArgumentException("mdfName must not be null/blank");

        try {
            return repository.findByModelKeyAndMdfName(modelKey, mdfName).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] findByModelKeyAndName failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelMdf> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) throw new IllegalArgumentException("modelKey must be > 0");
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            TypedQuery<TcModelMdfEntity> query = em.createQuery(
                    "SELECT e FROM TcModelMdfEntity e WHERE e.modelKey = :modelKey ORDER BY e.mdfKey DESC",
                    TcModelMdfEntity.class
            );
            query.setParameter("modelKey", modelKey);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());
            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] findAllByModelKey failed: modelKey=" + modelKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByMdfKey(long mdfKey) {
        if (mdfKey <= 0) {
            throw new IllegalArgumentException("mdfKey must be > 0");
        }
        try {
            repository.deleteById(mdfKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_mdf] deleteByMdfKey failed: mdfKey=" + mdfKey, e);
        }
    }

    private void validateUpsert(UpsertTcModelMdf command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.mdfKey() != null && command.mdfKey() <= 0) {
            throw new IllegalArgumentException("command.mdfKey must be > 0 when provided");
        }
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.mdfName() == null || command.mdfName().isBlank()) {
            throw new IllegalArgumentException("command.mdfName must not be null/blank");
        }
        if (command.mdfFile() == null || command.mdfFile().length == 0) {
            throw new IllegalArgumentException("command.mdfFile must not be null/empty");
        }
    }

    private TcModelMdfEntity resolveEntity(UpsertTcModelMdf command) {
        if (command.mdfKey() != null) {
            return repository.findById(command.mdfKey())
                    .orElseThrow(() -> new DbEntityNotFoundException("[tc_model_mdf] not found: mdfKey=" + command.mdfKey()));
        }

        return repository.findByModelKeyAndMdfName(command.modelKey(), command.mdfName())
                .orElseGet(() -> TcModelMdfEntity.newEntity(command.modelKey(), command.mdfName(), command.mdfFile()));
    }
}