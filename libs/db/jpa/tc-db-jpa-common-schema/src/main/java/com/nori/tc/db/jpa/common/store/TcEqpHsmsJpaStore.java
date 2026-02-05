package com.nori.tc.db.jpa.common.store;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpHsms;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.jpa.common.entity.TcEqpHsmsEntity;
import com.nori.tc.db.jpa.common.mapper.TcEqpHsmsEntityMapper;
import com.nori.tc.db.jpa.common.repository.TcEqpHsmsJpaRepository;

/**
 * tc_eqp_hsms JPA Store 구현체.
 *
 * <p>
 * <b>특이사항:</b>
 * HSMS 설정값(t3~t8, interval 등)이 많아 수동 매핑 시 실수가 잦은 영역입니다.
 * MapStruct를 통해 Command -> Entity 매핑을 100% 자동화했습니다.
 * </p>
 */
@Repository
public class TcEqpHsmsJpaStore implements TcEqpHsmsStore {

    private final TcEqpHsmsJpaRepository repository;
    private final TcEqpHsmsEntityMapper mapper;

    public TcEqpHsmsJpaStore(TcEqpHsmsJpaRepository repository, TcEqpHsmsEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpHsms upsert(UpsertTcEqpHsms command) {
        validateCommand(command);

        try {
            final long eqpKey = command.eqpKey();

            // 1. 조회 또는 신규 생성
            final TcEqpHsmsEntity entity = repository.findById(eqpKey)
                    .orElseGet(() -> TcEqpHsmsEntity.newEntity(eqpKey));

            // 2. [MapStruct] 전체 필드 자동 매핑
            mapper.updateEntity(command, entity);

            // 3. 저장 및 반환
            TcEqpHsmsEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_hsms] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpHsms> findByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        try {
            return repository.findById(eqpKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] findByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        try {
            repository.deleteById(eqpKey);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] deleteByEqpKey failed: eqpKey=" + eqpKey, e);
        }
    }

    private void validateCommand(UpsertTcEqpHsms command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be > 0");
        if (command.deviceId() < 0) throw new IllegalArgumentException("command.deviceId must be >= 0");
        if (command.connectionMode() == null || command.connectionMode().isBlank()) {
            throw new IllegalArgumentException("command.connectionMode must not be null/blank");
        }
        if (command.t3Timeout() <= 0) throw new IllegalArgumentException("command.t3Timeout must be > 0");
        if (command.t5Timeout() <= 0) throw new IllegalArgumentException("command.t5Timeout must be > 0");
        if (command.t6Timeout() <= 0) throw new IllegalArgumentException("command.t6Timeout must be > 0");
        if (command.t7Timeout() <= 0) throw new IllegalArgumentException("command.t7Timeout must be > 0");
        if (command.t8Timeout() <= 0) throw new IllegalArgumentException("command.t8Timeout must be > 0");
        if (command.linkTestInterval() <= 0) throw new IllegalArgumentException("command.linkTestInterval must be > 0");
        if (command.maxMsgBytes() <= 0) throw new IllegalArgumentException("command.maxMsgBytes must be > 0");
    }
}
