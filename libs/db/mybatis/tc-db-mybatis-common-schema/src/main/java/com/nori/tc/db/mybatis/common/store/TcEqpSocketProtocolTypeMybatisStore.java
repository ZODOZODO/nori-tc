package com.nori.tc.db.mybatis.common.store;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.TcEqpSocketProtocolTypeStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpSocketProtocolType;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpSocketProtocolType;
import com.nori.tc.db.mybatis.common.mapper.TcEqpSocketProtocolTypeMapper;

/**
 * tc_eqp_socket_protocol_type MyBatis Store 구현체.
 */
@Repository
public class TcEqpSocketProtocolTypeMybatisStore implements TcEqpSocketProtocolTypeStore {

    private final TcEqpSocketProtocolTypeMapper mapper;

    public TcEqpSocketProtocolTypeMybatisStore(TcEqpSocketProtocolTypeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcEqpSocketProtocolType upsert(UpsertTcEqpSocketProtocolType command) {
        validateCommand(command);

        final TcEqpSocketProtocolType row = new TcEqpSocketProtocolType(
                command.socketProtocolType(),
                command.socketProtocolTypeName(),
                command.parseStartRule(),
                command.parseEndRule(),
                command.parseRegex(),
                command.description()
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

            return mapper.findBySocketProtocolType(command.socketProtocolType())
                    .orElseThrow(() -> new DbAccessException("tc_eqp_socket_protocol_type upsert succeeded but row not found. socketProtocolType=" + command.socketProtocolType()));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException("tc_eqp_socket_protocol_type upsert duplicate key. socketProtocolType=" + command.socketProtocolType(), e);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type upsert failed. socketProtocolType=" + command.socketProtocolType(), e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type upsert failed (unexpected). socketProtocolType=" + command.socketProtocolType(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpSocketProtocolType> findBySocketProtocolType(String socketProtocolType) {
        try {
            return mapper.findBySocketProtocolType(socketProtocolType);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type findBySocketProtocolType failed. socketProtocolType=" + socketProtocolType, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type findBySocketProtocolType failed (unexpected). socketProtocolType=" + socketProtocolType, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpSocketProtocolType> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            return mapper.findAll(p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type findAll failed", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type findAll failed (unexpected)", e);
        }
    }

    @Override
    @Transactional
    public void deleteBySocketProtocolType(String socketProtocolType) {
        try {
            mapper.deleteBySocketProtocolType(socketProtocolType);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type deleteBySocketProtocolType failed. socketProtocolType=" + socketProtocolType, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_eqp_socket_protocol_type deleteBySocketProtocolType failed (unexpected). socketProtocolType=" + socketProtocolType, e);
        }
    }

    private void validateCommand(UpsertTcEqpSocketProtocolType command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.socketProtocolType() == null || command.socketProtocolType().isBlank()) {
            throw new IllegalArgumentException("command.socketProtocolType must not be null/blank");
        }
        if (command.socketProtocolTypeName() == null || command.socketProtocolTypeName().isBlank()) {
            throw new IllegalArgumentException("command.socketProtocolTypeName must not be null/blank");
        }
    }
}
