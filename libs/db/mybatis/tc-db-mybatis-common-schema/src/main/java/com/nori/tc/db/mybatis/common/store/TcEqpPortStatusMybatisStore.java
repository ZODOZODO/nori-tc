package com.nori.tc.db.mybatis.common.store;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpPortStatusStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpPortStatus;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpPortStatus;
import com.nori.tc.db.mybatis.common.mapper.TcEqpPortStatusMapper;

/**
 * tc_eqp_port_status MyBatis Store 구현체.
 *
 * - Unique: (eqp_key, port_id)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 */
@Repository
public class TcEqpPortStatusMybatisStore implements TcEqpPortStatusStore {

    private final TcEqpPortStatusMapper mapper;

    public TcEqpPortStatusMybatisStore(TcEqpPortStatusMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpPortStatus upsert(UpsertTcEqpPortStatus command) {
        validateCommand(command);

        final long eqpKey = command.eqpKey();
        final String portId = command.portId();

        final TcEqpPortStatus row = new TcEqpPortStatus(
                0L,
                eqpKey,
                portId,
                command.portType(),
                command.portState(),
                command.carrierId(),
                command.carrierType(),
                command.carrierState(),
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

            return mapper.findByEqpKeyPortId(eqpKey, portId)
                    .orElseThrow(() -> new DbAccessException("tc_eqp_port_status upsert succeeded but row not found. eqpKey/portId=" + eqpKey + "/" + portId));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_port_status upsert duplicate key. eqpKey/portId=" + eqpKey + "/" + portId, e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_port_status upsert failed. eqpKey/portId=" + eqpKey + "/" + portId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_port_status upsert failed (unexpected). eqpKey/portId=" + eqpKey + "/" + portId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpPortStatus> findByEqpKeyPortId(long eqpKey, String portId) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (portId == null || portId.isBlank()) {
            throw new IllegalArgumentException("portId must not be null/blank");
        }
        try {
            return mapper.findByEqpKeyPortId(eqpKey, portId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_port_status findByEqpKeyPortId failed. eqpKey/portId=" + eqpKey + "/" + portId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_port_status findByEqpKeyPortId failed (unexpected). eqpKey/portId=" + eqpKey + "/" + portId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpPortStatus> findAllByEqpKey(long eqpKey, PageRequest page) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAllByEqpKey(eqpKey, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_port_status findAllByEqpKey failed. eqpKey=" + eqpKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_port_status findAllByEqpKey failed (unexpected). eqpKey=" + eqpKey, e);
        }
    }

    @Override
    @Transactional
    public void deleteByEqpKeyPortId(long eqpKey, String portId) {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (portId == null || portId.isBlank()) {
            throw new IllegalArgumentException("portId must not be null/blank");
        }
        try {
            mapper.deleteByEqpKeyPortId(eqpKey, portId);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_port_status deleteByEqpKeyPortId failed. eqpKey/portId=" + eqpKey + "/" + portId, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_port_status deleteByEqpKeyPortId failed (unexpected). eqpKey/portId=" + eqpKey + "/" + portId, e);
        }
    }

    private void validateCommand(UpsertTcEqpPortStatus command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.eqpKey() <= 0) throw new IllegalArgumentException("command.eqpKey must be > 0");
        if (command.portId() == null || command.portId().isBlank()) throw new IllegalArgumentException("command.portId must not be null/blank");
    }
}
