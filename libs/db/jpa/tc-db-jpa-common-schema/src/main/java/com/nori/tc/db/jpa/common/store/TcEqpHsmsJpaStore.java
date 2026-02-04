package com.nori.tc.db.jpa.common.store;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpHsms;
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
            final String eqpId = command.eqpId();

            // 1. 조회 또는 신규 생성
            final TcEqpHsmsEntity entity = repository.findById(eqpId)
                    .orElseGet(() -> TcEqpHsmsEntity.newEntity(eqpId));

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
    public Optional<TcEqpHsms> findByEqpId(String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId must not be null/blank");
        }
        try {
            return repository.findById(eqpId).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_hsms] findByEqpId failed: eqpId=" + eqpId, e);
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
            throw new DbAccessException("[tc_eqp_hsms] deleteByEqpId failed: eqpId=" + eqpId, e);
        }
    }

    private void validateCommand(UpsertTcEqpHsms command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpId() == null || command.eqpId().isBlank()) throw new IllegalArgumentException("command.eqpId must not be null/blank");
    }
}