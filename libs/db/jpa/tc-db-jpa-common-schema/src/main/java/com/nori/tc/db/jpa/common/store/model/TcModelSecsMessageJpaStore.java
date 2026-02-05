package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.model.NewTcModelSecsMessage;
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
 * <li><b>Create/Update 분리:</b> 생성과 수정 Command가 분리되어 있으며, MapStruct를 통해 각각 최적화된 매핑을 수행합니다.</li>
 * <li><b>모델별 메시지 조회:</b> model_key 기반 목록 조회 및 유니크 키 조회를 지원합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelSecsMessageJpaStore implements TcModelSecsMessageStore {

    private final TcModelSecsMessageJpaRepository repository;
    private final TcModelSecsMessageEntityMapper mapper;

    public TcModelSecsMessageJpaStore(
            TcModelSecsMessageJpaRepository repository,
            TcModelSecsMessageEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelSecsMessage create(NewTcModelSecsMessage command) {
        validateCreate(command);

        try {
            TcModelSecsMessageEntity entity = TcModelSecsMessageEntity.newEntity(
                    command.modelKey(),
                    command.secsMsgName()
            );

            mapper.updateFromNew(command, entity);

            TcModelSecsMessageEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_secs_message] create failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] create failed", e);
        }
    }

    @Override
    @Transactional
    public TcModelSecsMessage update(UpsertTcModelSecsMessage command) {
        validateUpdate(command);

        try {
            TcModelSecsMessageEntity entity = repository.findById(command.secsMsgKey())
                    .orElseThrow(() -> new DbEntityNotFoundException("[tc_model_secs_message] not found: secsMsgKey=" + command.secsMsgKey()));

            mapper.updateFromUpdate(command, entity);

            TcModelSecsMessageEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DbEntityNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_secs_message] update failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] update failed: secsMsgKey=" + command.secsMsgKey(), e);
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
    public List<TcModelSecsMessage> findByModelKey(long modelKey) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        try {
            return repository.findByModelKey(modelKey).stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_secs_message] findByModelKey failed: modelKey=" + modelKey, e);
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

    private void validateCreate(NewTcModelSecsMessage command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.secsMsgName() == null || command.secsMsgName().isBlank()) {
            throw new IllegalArgumentException("command.secsMsgName must not be null/blank");
        }
    }

    private void validateUpdate(UpsertTcModelSecsMessage command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.secsMsgKey() <= 0) throw new IllegalArgumentException("command.secsMsgKey must be > 0");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.secsMsgName() == null || command.secsMsgName().isBlank()) {
            throw new IllegalArgumentException("command.secsMsgName must not be null/blank");
        }
    }
}
