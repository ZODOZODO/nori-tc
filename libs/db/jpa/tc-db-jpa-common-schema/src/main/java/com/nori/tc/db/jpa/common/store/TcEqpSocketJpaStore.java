package com.nori.tc.db.jpa.common.store;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpSocket;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import com.nori.tc.db.jpa.common.entity.TcEqpSocketEntity;
import com.nori.tc.db.jpa.common.mapper.TcEqpSocketEntityMapper;
import com.nori.tc.db.jpa.common.repository.TcEqpSocketJpaRepository;

/**
 * tc_eqp_socket JPA Store 구현체.
 *
 * <p>
 * <b>Charset 정책:</b>
 * DB 컬럼은 NOT NULL입니다. 커맨드로 들어온 charset이 null/blank인 경우,
 * 애플리케이션 레벨에서 기본값(UTF-8)을 강제하여 DB 제약을 만족시킵니다.
 * </p>
 */
@Repository
public class TcEqpSocketJpaStore implements TcEqpSocketStore {

    private static final String DEFAULT_CHARSET = "UTF-8";

    private final TcEqpSocketJpaRepository repository;
    private final TcEqpSocketEntityMapper mapper;

    public TcEqpSocketJpaStore(TcEqpSocketJpaRepository repository, TcEqpSocketEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpSocket upsert(UpsertTcEqpSocket command) {
        validateCommand(command);

        try {
            final String eqpId = command.eqpId();

            final TcEqpSocketEntity entity = repository.findById(eqpId)
                    .orElseGet(() -> TcEqpSocketEntity.newEntity(eqpId));

            // 1. [MapStruct] 일반 필드 매핑
            mapper.updateEntity(command, entity);

            // 2. [Manual Logic] Charset 기본값 처리
            final String charset = (command.charset() == null || command.charset().isBlank())
                    ? DEFAULT_CHARSET
                    : command.charset();
            entity.setCharset(charset);

            // 3. 저장 및 반환
            TcEqpSocketEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_socket] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpSocket> findByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            return repository.findById(eqpId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket] findByEqpId failed: eqpId=" + eqpId, e);
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
            throw new DbAccessException("[tc_eqp_socket] deleteByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    private void validateCommand(UpsertTcEqpSocket command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpId() == null || command.eqpId().isBlank()) throw new IllegalArgumentException("command.eqpId must not be null/blank");
        if (command.socketProtocolType() == null || command.socketProtocolType().isBlank()) throw new IllegalArgumentException("command.socketProtocolType must not be null/blank");
    }
}