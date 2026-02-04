package com.nori.tc.db.mybatis.common.store;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpLogStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpLog;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpLog;
import com.nori.tc.db.mybatis.common.mapper.TcEqpLogMapper;

/**
 * tc_eqp_log MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_id)
 */
@Repository
public class TcEqpLogMybatisStore implements TcEqpLogStore {

    private final TcEqpLogMapper mapper;

    public TcEqpLogMybatisStore(TcEqpLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpLog upsert(UpsertTcEqpLog command) {
        final String eqpId = command.eqpId();

        final TcEqpLog row = new TcEqpLog(
                eqpId,
                command.logLevel(),
                command.logPath(),
                command.createdAt(),
                command.updatedAt()
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
                    .orElseThrow(() -> new DbAccessException("tc_eqp_log upsert succeeded but row not found. eqpId=" + eqpId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_log upsert duplicate key. eqpId=" + eqpId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_log upsert failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_log upsert failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpLog> findByEqpId(String eqpId) {
        try {
            return mapper.findByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_log findByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_log findByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        try {
            mapper.deleteByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_log deleteByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_log deleteByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }
}
