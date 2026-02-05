package com.nori.tc.db.jpa.common.store.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.model.store.TcModelSocketMessageStore;
import com.nori.tc.db.core.model.upsert.UpsertTcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;
import com.nori.tc.db.jpa.common.entity.model.TcModelSocketMessageEntity;
import com.nori.tc.db.jpa.common.mapper.model.TcModelSocketMessageEntityMapper;
import com.nori.tc.db.jpa.common.repository.model.TcModelSocketMessageJpaRepository;

/**
 * tc_model_socket_message JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> (model_key, socket_msg_name) 유니크 키로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcModelSocketMessageJpaStore implements TcModelSocketMessageStore {

    private final TcModelSocketMessageJpaRepository repository;
    private final TcModelSocketMessageEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcModelSocketMessageJpaStore(
            TcModelSocketMessageJpaRepository repository,
            TcModelSocketMessageEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcModelSocketMessage upsert(UpsertTcModelSocketMessage command) {
        validateCommand(command);

        try {
            final long modelKey = command.modelKey();
            final String socketMsgName = command.socketMsgName();

            TcModelSocketMessageEntity entity = repository.findByModelKeyAndSocketMsgName(modelKey, socketMsgName)
                    .orElseGet(() -> TcModelSocketMessageEntity.newEntity(modelKey, socketMsgName));

            mapper.updateEntity(command, entity);

            TcModelSocketMessageEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_model_socket_message] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_socket_message] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcModelSocketMessage> findByModelKeySocketMsgName(long modelKey, String socketMsgName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (socketMsgName == null || socketMsgName.isBlank()) {
            throw new IllegalArgumentException("socketMsgName must not be null/blank");
        }
        try {
            return repository.findByModelKeyAndSocketMsgName(modelKey, socketMsgName).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_socket_message] findByModelKeySocketMsgName failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcModelSocketMessage> findAllByModelKey(long modelKey, PageRequest page) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcModelSocketMessageEntity> cq = cb.createQuery(TcModelSocketMessageEntity.class);
            Root<TcModelSocketMessageEntity> root = cq.from(TcModelSocketMessageEntity.class);

            Predicate predicate = cb.equal(root.get("modelKey"), modelKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("socketMsgName")), cb.asc(root.get("socketMsgKey")));

            TypedQuery<TcModelSocketMessageEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_socket_message] findAllByModelKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByModelKeySocketMsgName(long modelKey, String socketMsgName) {
        if (modelKey <= 0) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }
        if (socketMsgName == null || socketMsgName.isBlank()) {
            throw new IllegalArgumentException("socketMsgName must not be null/blank");
        }
        try {
            repository.findByModelKeyAndSocketMsgName(modelKey, socketMsgName).ifPresent(repository::delete);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_model_socket_message] deleteByModelKeySocketMsgName failed", e);
        }
    }

    private void validateCommand(UpsertTcModelSocketMessage command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.modelKey() <= 0) throw new IllegalArgumentException("command.modelKey must be > 0");
        if (command.socketMsgName() == null || command.socketMsgName().isBlank()) {
            throw new IllegalArgumentException("command.socketMsgName must not be null/blank");
        }
    }
}
