package com.nori.tc.db.mybatis.common.store;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpOperStateStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpOperState;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpOperState;
import com.nori.tc.db.mybatis.common.mapper.TcEqpOperStateMapper;

/**
 * tc_eqp_oper_state MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_id)
 * - upsert는 update-first 전략으로 구현
 */
@Repository
public class TcEqpOperStateMybatisStore implements TcEqpOperStateStore {

    private final TcEqpOperStateMapper mapper;

    public TcEqpOperStateMybatisStore(TcEqpOperStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpOperState upsert(UpsertTcEqpOperState command) {
        final String eqpId = command.eqpId();

        final TcEqpOperState row = new TcEqpOperState(
                eqpId,
                command.operState(),
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

            return mapper.findByEqpId(eqpId)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_oper_state upsert succeeded but row not found. eqpId=" + eqpId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_oper_state upsert duplicate key. eqpId=" + eqpId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_oper_state upsert failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_oper_state upsert failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpOperState> findByEqpId(String eqpId) {
        try {
            return mapper.findByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_oper_state findByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_oper_state findByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        try {
            mapper.deleteByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_oper_state deleteByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_oper_state deleteByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }
}
