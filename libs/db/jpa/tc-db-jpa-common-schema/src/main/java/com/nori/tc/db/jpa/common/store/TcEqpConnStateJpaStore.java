package com.nori.tc.db.jpa.common.store;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpConnStateStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpConnState;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpConnState;
import com.nori.tc.db.jpa.common.entity.TcEqpConnStateEntity;
import com.nori.tc.db.jpa.common.mapper.TcEqpConnStateEntityMapper;
import com.nori.tc.db.jpa.common.repository.TcEqpConnStateJpaRepository;

/**
 * tc_eqp_conn_state JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략 (Hybrid Mapping):</b>
 * <ul>
 * <li><b>Auto Mapping:</b> 단순 필드(connState, lastXXX 등)는 MapStruct가 처리합니다.</li>
 * <li><b>Manual Logic:</b> 비즈니스 로직이 포함된 필드(sinceAt)는 Store에서 직접 제어합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcEqpConnStateJpaStore implements TcEqpConnStateStore {

    private final TcEqpConnStateJpaRepository repository;
    private final TcEqpConnStateEntityMapper mapper;

    public TcEqpConnStateJpaStore(TcEqpConnStateJpaRepository repository, TcEqpConnStateEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpConnState upsert(UpsertTcEqpConnState command) {
        validateCommand(command);

        try {
            final String eqpId = command.eqpId();

            // 1. 조회 또는 신규 생성 (Entity Factory 사용)
            final TcEqpConnStateEntity entity = repository.findById(eqpId)
                    .orElseGet(() -> TcEqpConnStateEntity.newEntity(eqpId));

            // 2. [MapStruct] 일반 필드 자동 매핑
            mapper.updateEntity(command, entity);

            // 3. [Manual Logic] sinceAt 처리
            // - Command에 값이 있으면: 덮어쓰기
            // - Command에 값이 없고, 신규 Entity라면: now() 설정
            // - Command에 값이 없고, 기존 Entity라면: 기존 값 유지
            if (command.sinceAt() != null) {
                entity.setSinceAt(command.sinceAt());
            } else if (entity.getSinceAt() == null) {
                entity.setSinceAt(OffsetDateTime.now());
            }

            // 4. 저장 및 반환
            TcEqpConnStateEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_conn_state] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_conn_state] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpConnState> findByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            return repository.findById(eqpId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_conn_state] findByEqpId failed: eqpId=" + eqpId, e);
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
            // 삭제 대상이 없어도 성공으로 간주 (Idempotent)
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_conn_state] deleteByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    private void validateCommand(UpsertTcEqpConnState command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpId() == null || command.eqpId().isBlank()) throw new IllegalArgumentException("command.eqpId must not be null/blank");
        if (command.connState() == null) throw new IllegalArgumentException("command.connState must not be null");
    }
}