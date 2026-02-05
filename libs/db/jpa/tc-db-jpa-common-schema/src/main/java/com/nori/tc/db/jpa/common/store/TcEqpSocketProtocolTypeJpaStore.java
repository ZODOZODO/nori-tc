package com.nori.tc.db.jpa.common.store;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.TcEqpSocketProtocolTypeStore;
import com.nori.tc.db.core.eqp.UpsertTcEqpSocketProtocolType;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.domain.eqp.TcEqpSocketProtocolType;
import com.nori.tc.db.jpa.common.entity.TcEqpSocketProtocolTypeEntity;
import com.nori.tc.db.jpa.common.mapper.TcEqpSocketProtocolTypeEntityMapper;
import com.nori.tc.db.jpa.common.repository.TcEqpSocketProtocolTypeJpaRepository;

/**
 * tc_eqp_socket_protocol_type JPA Store 구현체.
 */
@Repository
public class TcEqpSocketProtocolTypeJpaStore implements TcEqpSocketProtocolTypeStore {

    private final TcEqpSocketProtocolTypeJpaRepository repository;
    private final TcEqpSocketProtocolTypeEntityMapper mapper;
    private final EntityManager em;

    public TcEqpSocketProtocolTypeJpaStore(TcEqpSocketProtocolTypeJpaRepository repository,
                                          TcEqpSocketProtocolTypeEntityMapper mapper,
                                          EntityManager em) {
        this.repository = repository;
        this.mapper = mapper;
        this.em = em;
    }

    @Override
    @Transactional
    public TcEqpSocketProtocolType upsert(UpsertTcEqpSocketProtocolType command) {
        validateCommand(command);

        try {
            final String socketProtocolType = command.socketProtocolType();

            final TcEqpSocketProtocolTypeEntity entity = repository.findById(socketProtocolType)
                    .orElseGet(() -> TcEqpSocketProtocolTypeEntity.newEntity(socketProtocolType));

            mapper.updateEntity(command, entity);

            TcEqpSocketProtocolTypeEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_eqp_socket_protocol_type] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket_protocol_type] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcEqpSocketProtocolType> findBySocketProtocolType(String socketProtocolType) {
        if (socketProtocolType == null || socketProtocolType.isBlank()) {
            throw new IllegalArgumentException("socketProtocolType must not be null/blank");
        }

        try {
            return repository.findById(socketProtocolType).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket_protocol_type] findBySocketProtocolType failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcEqpSocketProtocolType> findAll(PageRequest page) {
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            TypedQuery<TcEqpSocketProtocolTypeEntity> query = em.createQuery(
                    "SELECT e FROM TcEqpSocketProtocolTypeEntity e ORDER BY e.socketProtocolType",
                    TcEqpSocketProtocolTypeEntity.class
            );
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();

        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket_protocol_type] findAll failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteBySocketProtocolType(String socketProtocolType) {
        if (socketProtocolType == null || socketProtocolType.isBlank()) {
            throw new IllegalArgumentException("socketProtocolType must not be null/blank");
        }

        try {
            repository.deleteById(socketProtocolType);
        } catch (EmptyResultDataAccessException ignore) {
            // Idempotent delete
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_eqp_socket_protocol_type] deleteBySocketProtocolType failed", e);
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
