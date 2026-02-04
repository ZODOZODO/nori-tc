package com.nori.tc.db.mybatis.common.store;

import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.eqp.TcEqpHsmsStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpHsms;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.mybatis.common.mapper.TcEqpHsmsMapper;

/**
 * tc_eqp_hsms MyBatis Store 구현체.
 *
 * - 1:1 테이블 (PK=eqp_id)
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
        final String eqpId = command.eqpId();

        final TcEqpHsms row = new TcEqpHsms(
                eqpId,
                command.deviceId(),
                command.t3Ms(),
                command.t5Ms(),
                command.t6Ms(),
                command.t7Ms(),
                command.t8Ms(),
                command.linktestEnabled(),
                command.linktestIntervalMs(),
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

            return mapper.findByEqpId(eqpId)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_hsms upsert succeeded but row not found. eqpId=" + eqpId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_hsms upsert duplicate key. eqpId=" + eqpId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms upsert failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms upsert failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpHsms> findByEqpId(String eqpId) {
        try {
            return mapper.findByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms findByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms findByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpId(String eqpId) {
        try {
            mapper.deleteByEqpId(eqpId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_hsms deleteByEqpId failed. eqpId=" + eqpId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_hsms deleteByEqpId failed (unexpected). eqpId=" + eqpId, e);
        }
    }
}
