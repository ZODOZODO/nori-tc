package com.nori.tc.db.mybatis.common.store;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.store.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpHsms;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.mybatis.common.mapper.TcEqpHsmsMapper;

/**
 * tc_eqp_hsms MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_key)
 * - created_at/updated_at은 DB/SQL이 관리(CURRENT_TIMESTAMP)하므로
 *   command의 createdAt/updatedAt은 "입력 DTO"로만 보관되고 실제 SQL에서는 반영되지 않을 수 있다.
 */
@Repository
public class TcEqpHsmsMybatisStore implements TcEqpHsmsStore {

    private final TcEqpHsmsMapper mapper;

    public TcEqpHsmsMybatisStore(TcEqpHsmsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpHsms upsert(UpsertTcEqpHsms command) {
        final long eqpKey = command.eqpKey();

        final TcEqpHsms row = new TcEqpHsms(
                eqpKey,
                command.deviceId(),
                command.connectionMode(),
                command.t3Timeout(),
                command.t5Timeout(),
                command.t6Timeout(),
                command.t7Timeout(),
                command.t8Timeout(),
                command.linkTestEnabled(),
                command.linkTestInterval(),
                command.maxMsgBytes(),
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
                    .orElseThrow(() -> new DbAccessException("tc_eqp_hsms upsert succeeded but row not found. eqpKey=" + eqpKey));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_hsms upsert duplicate key. eqpKey=" + eqpKey, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms upsert failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms upsert failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpHsms> findByEqpKey(long eqpKey) {
        try {
            return mapper.findByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms findByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms findByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKey(long eqpKey) {
        try {
            mapper.deleteByEqpKey(eqpKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms deleteByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms deleteByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }
}
