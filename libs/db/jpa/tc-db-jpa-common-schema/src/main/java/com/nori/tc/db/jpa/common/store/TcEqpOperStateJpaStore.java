package com.nori.tc.db.jpa.common.store;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpOperStateStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpOperState;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpOperState;
import com.nori.tc.db.jpa.common.entity.TcEqpOperStateEntity;
import com.nori.tc.db.jpa.common.mapper.TcEqpOperStateEntityMapper;
import com.nori.tc.db.jpa.common.repository.TcEqpOperStateJpaRepository;

/**
 * tc_eqp_oper_state JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략:</b>
 * - 일반 필드는 MapStruct로 자동 처리합니다.
 * - sinceAt 필드는 "기존 값 유지 or 신규 시 now()" 규칙을 위해 Store에서 수동 제어합니다.
 * </p>
 */
@Repository
public class TcEqpOperStateJpaStore implements TcEqpOperStateStore {

    private final TcEqpOperStateJpaRepository repository;
    private final TcEqpOperStateEntityMapper mapper;

    public TcEqpOperStateJpaStore(TcEqpOperStateJpaRepository repository, TcEqpOperStateEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpOperState upsert(UpsertTcEqpOperState command) {
        validateCommand(command);

        try {
            final String eqpId = command.eqpId();

            final TcEqpOperStateEntity entity = repository.findById(eqpId)
                    .orElseGet(() -> TcEqpOperStateEntity.newEntity(eqpId));

            // 1. [MapStruct] 일반 필드 매핑
            mapper.updateEntity(command, entity);

            // 2. [Manual Logic] sinceAt 처리
            if (command.sinceAt() != null) {
                entity.setSinceAt(command.sinceAt());
            } else if (entity.getSinceAt() == null) {
                entity.setSinceAt(OffsetDateTime.now());
            }

            TcEqpOperStateEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_oper_state] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_oper_state] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpOperState> findByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            return repository.findById(eqpId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_oper_state] findByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            repository.deleteById(eqpId);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_oper_state] deleteByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    private void validateCommand(UpsertTcEqpOperState command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpId() == null || command.eqpId().isBlank()) throw new IllegalArgumentException("command.eqpId must not be null/blank");
        if (command.operState() == null || command.operState().isBlank()) throw new IllegalArgumentException("command.operState must not be null/blank");
    }
}