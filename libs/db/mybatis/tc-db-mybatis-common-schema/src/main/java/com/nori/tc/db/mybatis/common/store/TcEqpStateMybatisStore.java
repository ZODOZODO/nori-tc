package com.nori.tc.db.mybatis.common.store;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpStateStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpState;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpState;
import com.nori.tc.db.mybatis.common.mapper.TcEqpStateMapper;

/**
 * tc_eqp_state MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_key)
 * - upsert는 update-first 전략으로 구현
 */
@Repository
public class TcEqpStateMybatisStore implements TcEqpStateStore {

    private final TcEqpStateMapper mapper;

    public TcEqpStateMybatisStore(TcEqpStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpState upsert(UpsertTcEqpState command) {
        final long eqpKey = command.eqpKey();

        final TcEqpState row = new TcEqpState(
                eqpKey,
                command.controlState(),
                command.eqpState(),
                command.sinceAt(),
                command.reasonCode(),
                command.reasonDetail(),
                command.updatedAt() // SQL은 CURRENT_TIMESTAMP로 갱신
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                try {
                    mapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    mapper.update(row);
                }
            }

            return mapper.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_state upsert succeeded but row not found. eqpKey=" + eqpKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_state upsert duplicate key. eqpKey=" + eqpKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_state upsert failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_state upsert failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpState> findByEqpKey(long eqpKey) {
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_state findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_state findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        try {
            mapper.deleteByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_state deleteByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_state deleteByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }
}
