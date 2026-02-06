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
import com.nori.tc.db.core.model.store.TcModelSecsMessageStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.jpa.common.entity.model.TcModelSecsMessageEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelSecsMessageEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelSecsMessageJpaRepository;

/**
 * tc_model_secs_message JPA Store 구현체.
 *
 * <p>
 * <b>주요 기능:</b>
 * <ul>
 * <li><b>Upsert:</b> SECS 메시지 키 또는 유니크 키로 존재 여부를 확인한 뒤 생성/갱신을 수행합니다.</li>
 * <li><b>모델별 메시지 조회:</b> model_key 기반 목록 조회 및 유니크 키 조회를 지원합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelSecsMessageJpaStore implements TcModelSecsMessageStore {

    private final TcModelSecsMessageJpaRepository repository;
    private final TcModelSecsMessageEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcModelSecsMessageJpaStore(
            TcModelSecsMessageJpaRepository repository,
            TcModelSecsMessageEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelSecsMessage upsert(UpsertTcModelSecsMessage command) {
        validateUpsert(command);

        try {
            TcModelSecsMessageEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcModelSecsMessageEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DbEntityNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_secs_message] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSecsMessage> findBySecsMsgKey(long secsMsgKey) {
        if (secsMsgKey <= 0) {
            throw new IllegalArgumentException("secsMsgKey must be > 0");
        }
        try {
            return repository.findById(secsMsgKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] findBySecsMsgKey failed: secsMsgKey=" + secsMsgKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSecsMessage> findByModelKeyAndName(long modelKey, String secsMsgName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (secsMsgName == null || secsMsgName.isBlank()) {
            throw new IllegalArgumentException("secsMsgName must not be null/blank");
        }
        try {
            return repository.findByModelKeyAndSecsMsgName(modelKey, secsMsgName).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] findByModelKeyAndName failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelSecsMessage> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;
        try {
            TypedQuery<TcModelSecsMessageEntity> query = em.createQuery(
                    "SELECT e FROM TcModelSecsMessageEntity e WHERE e.modelKey = :modelKey ORDER BY e.secsMsgKey ASC",
                    TcModelSecsMessageEntity.class
            );
            query.setParameter("modelKey", modelKey);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] findAllByModelKey failed: modelKey=" + modelKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteBySecsMsgKey(long secsMsgKey) {
        if (secsMsgKey <= 0) {
            throw new IllegalArgumentException("secsMsgKey must be > 0");
        }
        try {
            repository.deleteById(secsMsgKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] deleteBySecsMsgKey failed: secsMsgKey=" + secsMsgKey, e);
        }
    }

    private void validateUpsert(UpsertTcModelSecsMessage command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.secsMsgKey() != null && command.secsMsgKey() <= 0) {
            throw new IllegalArgumentException("command.secsMsgKey must be > 0 when provided");
        }
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.secsMsgName() == null || command.secsMsgName().isBlank()) {
            throw new IllegalArgumentException("command.secsMsgName must not be null/blank");
        }
    }

    private TcModelSecsMessageEntity resolveEntity(UpsertTcModelSecsMessage command) {
        if (command.secsMsgKey() != null) {
            return repository.findById(command.secsMsgKey())
                    .orElseThrow(() -> new DbEntityNotFoundException("[tc_model_secs_message] not found: secsMsgKey=" + command.secsMsgKey()));
        }

        return repository.findByModelKeyAndSecsMsgName(command.modelKey(), command.secsMsgName())
                .orElseGet(() -> TcModelSecsMessageEntity.newEntity(command.modelKey(), command.secsMsgName()));
    }
}