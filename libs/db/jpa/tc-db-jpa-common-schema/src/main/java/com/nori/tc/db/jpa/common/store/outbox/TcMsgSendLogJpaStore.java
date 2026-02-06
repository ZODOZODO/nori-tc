package com.nori.tc.db.jpa.common.store.outbox;

import java.time.OffsetDateTime;
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
import com.nori.tc.db.core.outbox.store.TcMsgSendLogStore;
import com.nori.tc.db.core.outbox.upsert.UpsertTcMsgSendLog;
import com.nori.tc.db.domain.outbox.TcMsgSendLog;
import com.nori.tc.db.jpa.common.entity.outbox.TcMsgSendLogEntity;
import com.nori.tc.db.jpa.common.mapper.outbox.TcMsgSendLogEntityMapper;
import com.nori.tc.db.jpa.common.repository.outbox.TcMsgSendLogJpaRepository;

/**
 * tc_msg_send_log JPA Store 구현체.
 *
 * <p>
 * <b>설계 전략:</b>
 * <ul>
 * <li><b>Upsert:</b> (msg_key, attempt_no) 조합으로 조회 후 저장합니다.</li>
 * <li><b>Paging:</b> PageRequest(offset/limit)를 Criteria API로 직접 적용합니다.</li>
 * </ul>
 * </p>
 */
@Repository
public class TcMsgSendLogJpaStore implements TcMsgSendLogStore {

    private final TcMsgSendLogJpaRepository repository;
    private final TcMsgSendLogEntityMapper mapper;

    @PersistenceContext
    private EntityManager em;

    public TcMsgSendLogJpaStore(TcMsgSendLogJpaRepository repository, TcMsgSendLogEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TcMsgSendLog upsert(UpsertTcMsgSendLog command) {
        UpsertTcMsgSendLog normalized = normalizeCommand(command);
        validateCommand(normalized);

        try {
            final long msgKey = normalized.msgKey();
            final int attemptNo = normalized.attemptNo();

            TcMsgSendLogEntity entity = repository.findByMsgKeyAndAttemptNo(msgKey, attemptNo)
                    .orElseGet(() -> TcMsgSendLogEntity.newEntity(msgKey, attemptNo));

            mapper.updateEntity(normalized, entity);

            TcMsgSendLogEntity saved = repository.save(entity);
            return mapper.toDomain(saved);

        } catch (DataIntegrityViolationException e) {
            throw new DbDuplicateKeyException("[tc_msg_send_log] upsert failed: integrity violation", e);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_log] upsert failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TcMsgSendLog> findByMsgKeyAttemptNo(long msgKey, int attemptNo) {
        if (msgKey <= 0) {
            throw new IllegalArgumentException("msgKey must be positive");
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be >= 1");
        }
        try {
            return repository.findByMsgKeyAndAttemptNo(msgKey, attemptNo).map(mapper::toDomain);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_log] findByMsgKeyAttemptNo failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcMsgSendLog> findAllByMsgKey(long msgKey, PageRequest page) {
        if (msgKey <= 0) {
            throw new IllegalArgumentException("msgKey must be positive");
        }
        final PageRequest p = (page == null) ? PageRequest.defaultPage() : page;

        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<TcMsgSendLogEntity> cq = cb.createQuery(TcMsgSendLogEntity.class);
            Root<TcMsgSendLogEntity> root = cq.from(TcMsgSendLogEntity.class);

            Predicate predicate = cb.equal(root.get("msgKey"), msgKey);
            cq.select(root).where(predicate);
            cq.orderBy(cb.asc(root.get("attemptNo")), cb.asc(root.get("sendLogKey")));

            TypedQuery<TcMsgSendLogEntity> query = em.createQuery(cq);
            query.setFirstResult(p.offset());
            query.setMaxResults(p.limit());

            return query.getResultList().stream().map(mapper::toDomain).toList();
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_log] findAllByMsgKey failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteByMsgKeyAttemptNo(long msgKey, int attemptNo) {
        if (msgKey <= 0) {
            throw new IllegalArgumentException("msgKey must be positive");
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be >= 1");
        }
        try {
            repository.findByMsgKeyAndAttemptNo(msgKey, attemptNo).ifPresent(repository::delete);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_log] deleteByMsgKeyAttemptNo failed", e);
        }
    }

    private void validateCommand(UpsertTcMsgSendLog command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.msgKey() == null || command.msgKey() <= 0) {
            throw new IllegalArgumentException("command.msgKey must be positive");
        }
        if (command.attemptNo() == null || command.attemptNo() < 1) {
            throw new IllegalArgumentException("command.attemptNo must be >= 1");
        }
        if (command.result() == null) {
            throw new IllegalArgumentException("command.result must not be null");
        }
    }

    private UpsertTcMsgSendLog normalizeCommand(UpsertTcMsgSendLog command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new UpsertTcMsgSendLog(
                command.msgKey(),
                command.attemptNo(),
                command.result(),
                command.kafkaPartition(),
                command.kafkaOffset(),
                command.errorCode(),
                command.errorMessage(),
                command.sentAt() == null ? OffsetDateTime.now() : command.sentAt()
        );
    }
}
