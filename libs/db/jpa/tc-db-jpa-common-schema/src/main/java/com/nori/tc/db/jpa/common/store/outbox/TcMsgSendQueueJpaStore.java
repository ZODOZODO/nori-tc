package com.nori.tc.db.jpa.common.store.outbox;

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
import com.nori.tc.db.core.exception.DbEntityNotFoundException;
import com.nori.tc.db.core.outbox.store.TcMsgSendQueueStore;
import com.nori.tc.db.core.outbox.upsert.UpsertTcMsgSendQueue;
import com.nori.tc.db.domain.common.outbox.TcMsgSendStatus;
import com.nori.tc.db.domain.outbox.TcMsgSendQueue;
import com.nori.tc.db.jpa.common.entity.outbox.TcMsgSendQueueEntity;
import com.nori.tc.db.jpa.common.mapper.outbox.TcMsgSendQueueEntityMapper;
import com.nori.tc.db.jpa.common.repository.outbox.TcMsgSendQueueJpaRepository;

/**
 * tc_msg_send_queue JPA Store 구현체.
 *
 * <p>
 * 설계 전략:
 * <ul>
 *     <li><b>Upsert:</b> msg_key가 있으면 PK 기준, 없으면 (topic, idempotency_key) 유니크 키로 조회 후 저장한다.</li>
 *     <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용한다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcMsgSendQueueJpaStore implements TcMsgSendQueueStore {

    private final TcMsgSendQueueJpaRepository repository;
    private final TcMsgSendQueueEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcMsgSendQueueJpaStore(
            TcMsgSendQueueJpaRepository repository,
            TcMsgSendQueueEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcMsgSendQueue upsert(UpsertTcMsgSendQueue command) {
        validateCommand(command);

        try {
            TcMsgSendQueueEntity entity = resolveEntity(command);

            mapper.updateFromUpsert(command, entity);

            TcMsgSendQueueEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_msg_send_queue] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_queue] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcMsgSendQueue> findByMsgKey(long msgKey) {
        if (msgKey <= 0) {
            throw new IllegalArgumentException("msgKey must be > 0");
        }
        try {
            return repository.findById(msgKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_queue] findByMsgKey failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcMsgSendQueue> findByTopicAndIdempotencyKey(String topic, String idempotencyKey) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be null/blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be null/blank");
        }
        try {
            return repository.findByTopicAndIdempotencyKey(topic, idempotencyKey).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_queue] findByTopicAndIdempotencyKey failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcMsgSendQueue> findAllByStatus(TcMsgSendStatus status, PageRequest page) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcMsgSendQueueEntity> cq = cb.createQuery(TcMsgSendQueueEntity.class);
            Root<TcMsgSendQueueEntity> root = cq.from(TcMsgSendQueueEntity.class);

            Predicate predicate = cb.equal(root.get("status"), status);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("nextRetryAt")), cb.asc(root.get("msgKey")));

            TypedQuery<TcMsgSendQueueEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_queue] findAllByStatus failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByMsgKey(long msgKey) {
        if (msgKey <= 0) {
            throw new IllegalArgumentException("msgKey must be > 0");
        }
        try {
            repository.deleteById(msgKey);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_queue] deleteByMsgKey failed", e);
        }
    }

    private void validateCommand(UpsertTcMsgSendQueue command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        if (command.msgKey() != null && command.msgKey() <= 0) {
            throw new IllegalArgumentException("command.msgKey must be > 0 when provided");
        }
        if (command.topic() == null || command.topic().isBlank()) {
            throw new IllegalArgumentException("command.topic must not be null/blank");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("command.idempotencyKey must not be null/blank");
        }
        if (command.payloadJson() == null || command.payloadJson().isBlank()) {
            throw new IllegalArgumentException("command.payloadJson must not be null/blank");
        }
        if (command.status() == null) {
            throw new IllegalArgumentException("command.status must not be null");
        }
        if (command.retryCount() < 0) {
            throw new IllegalArgumentException("command.retryCount must be >= 0");
        }
    }

    private TcMsgSendQueueEntity resolveEntity(UpsertTcMsgSendQueue command) {
        if (command.msgKey() != null) {
            return repository.findById(command.msgKey())
                    .orElseThrow(() -> new DbEntityNotFoundException(
                            "[tc_msg_send_queue] not found: msgKey=" + command.msgKey()
                    ));
        }

        return repository.findByTopicAndIdempotencyKey(command.topic(), command.idempotencyKey())
                .orElseGet(() -> TcMsgSendQueueEntity.newEntity(command.topic(), command.idempotencyKey()));
    }
}
