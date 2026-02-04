package com.nori.tc.db.mybatis.common.store;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpSocket;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import com.nori.tc.db.mybatis.common.mapper.TcEqpSocketMapper;

/**
 * tc_eqp_socket MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_id)
 * - charset 기본값('UTF-8')은 SQL에서 COALESCE로 안전 처리됨
 */
@Repository
public class TcEqpSocketMybatisStore implements TcEqpSocketStore {

    private final TcEqpSocketMapper mapper;

    public TcEqpSocketMybatisStore(TcEqpSocketMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpSocket upsert(UpsertTcEqpSocket command) {
        final String eqpId = command.eqpId();

        final TcEqpSocket row = new TcEqpSocket(
                eqpId,
                command.socketProtocolType(),
                command.charset(),
                command.heartbeatEnabled(),
                command.heartbeatIntervalMs(),
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
                    .orElseThrow(() -> new DbAccessException("tc_eqp_socket upsert succeeded but row not found. eqpId=" + eqpId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_socket upsert duplicate key. eqpId=" + eqpId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket upsert failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket upsert failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpSocket> findByEqpId(String eqpId) {
        try {
            return mapper.findByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket findByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket findByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        try {
            mapper.deleteByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket deleteByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket deleteByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }
}
