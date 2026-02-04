package com.nori.tc.db.mybatis.common.store;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpConnStateStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpConnState;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpConnState;
import com.nori.tc.db.mybatis.common.mapper.TcEqpConnStateMapper;

/**
 * tc_eqp_conn_state MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_id)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 */
@Repository
public class TcEqpConnStateMybatisStore implements TcEqpConnStateStore {

    private final TcEqpConnStateMapper mapper;

    public TcEqpConnStateMybatisStore(TcEqpConnStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpConnState upsert(UpsertTcEqpConnState command) {
        final String eqpId = command.eqpId();

        final TcEqpConnState row = new TcEqpConnState(
                eqpId,
                command.connState(),
                command.sinceAt(),
                command.lastConnectAt(),
                command.lastDisconnectAt(),
                command.lastRxAt(),
                command.lastTxAt(),
                command.lastErrorCode(),
                command.lastErrorMessage(),
                command.updatedAt() // SQL에서는 CURRENT_TIMESTAMP로 갱신(입력값은 참고용)
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
                    .orElseThrow(() -> new DbAccessException("tc_eqp_conn_state upsert succeeded but row not found. eqpId=" + eqpId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_conn_state upsert duplicate key. eqpId=" + eqpId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_conn_state upsert failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_conn_state upsert failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpConnState> findByEqpId(String eqpId) {
        try {
            return mapper.findByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_conn_state findByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_conn_state findByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        try {
            mapper.deleteByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_conn_state deleteByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_conn_state deleteByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }
}
