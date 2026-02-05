package com.nori.tc.db.mybatis.common.store;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpSocketStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocket;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import com.nori.tc.db.mybatis.common.mapper.TcEqpSocketMapper;

/**
 * tc_eqp_socket MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_key)
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
        final long eqpKey = command.eqpKey();

        final TcEqpSocket row = new TcEqpSocket(
                eqpKey,
                command.socketProtocolType(),
                command.connectionMode(),
                command.charset(),
                command.heartbeatEnabled(),
                command.heartbeatInterval(),
                command.readTimeout(),
                command.writeTimeout(),
                command.maxFrameSizeBytes(),
                command.keepAliveEnabled(),
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

            return mapper.findByEqpKey(eqpKey)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_socket upsert succeeded but row not found. eqpKey=" + eqpKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_socket upsert duplicate key. eqpKey=" + eqpKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket upsert failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket upsert failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpSocket> findByEqpKey(long eqpKey) {
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        try {
            mapper.deleteByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket deleteByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket deleteByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }
}
