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

    
    /**
     * DB JPA 계층 구성 요소를 초기화합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param repository DB JPA 계층 처리에 사용하는 입력 값
     * @param mapper DB JPA 계층 처리에 사용하는 입력 값
     */
    public TcMsgSendQueueJpaStore(
            TcMsgSendQueueJpaRepository repository,
            TcMsgSendQueueEntityMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    
    /**
     * DB JPA 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
    @Override
    @Transactional
    public TcMsgSendQueue upsert(UpsertTcMsgSendQueue command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param msgKey 대상 키 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param topic Kafka 토픽 이름
     * @param idempotencyKey 대상 키 값
     * @return 조회 결과(Optional)
     */
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

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param status DB JPA 계층 처리에 사용하는 입력 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
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

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param msgKey 대상 키 값
     */
    @Override
    @Transactional
    public void deleteByMsgKey(long msgKey) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        if (msgKey <= 0) {
            throw new IllegalArgumentException("msgKey must be > 0");
        }
        try {
            repository.deleteById(msgKey);
        } catch (RuntimeException e) {
            throw new DbAccessException("[tc_msg_send_queue] deleteByMsgKey failed", e);
        }
    }

    
    /**
     * DB JPA 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     */
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

    
    /**
     * DB JPA 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB JPA 계층 처리 결과
     */
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
