package com.nori.tc.db.mybatis.common.store.outbox;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.exception.DbAccessException;
import com.nori.tc.db.core.exception.DbDuplicateKeyException;
import com.nori.tc.db.core.outbox.store.TcMsgSendQueueStore;
import com.nori.tc.db.core.outbox.upsert.UpsertTcMsgSendQueue;
import com.nori.tc.db.domain.common.outbox.TcMsgSendStatus;
import com.nori.tc.db.domain.outbox.TcMsgSendQueue;
import com.nori.tc.db.mybatis.common.mapper.outbox.TcMsgSendQueueMapper;

/**
 * tc_msg_send_queue MyBatis Store 구현체.
 *
 * <p>
 * - Unique: (topic, idempotency_key)
 * - upsert는 update-first 전략으로 벤더 중립 구현
 * </p>
 */
@Repository
public class TcMsgSendQueueMybatisStore implements TcMsgSendQueueStore {

    private final TcMsgSendQueueMapper mapper;

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param mapper DB MyBatis 계층 처리에 사용하는 입력 값
     */
    public TcMsgSendQueueMybatisStore(TcMsgSendQueueMapper mapper) {
        this.mapper = mapper;
    }

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    @Override
    @Transactional
    public TcMsgSendQueue upsert(UpsertTcMsgSendQueue command) {
        // 저장 단계: 변경 내용을 저장소에 반영하고 결과를 확인합니다.
        validateCommand(command);

        final long resolvedKey = resolveKey(command);

        final TcMsgSendQueue row = new TcMsgSendQueue(
                resolvedKey,
                command.idempotencyKey(),
                command.topic(),
                command.messageKey(),
                command.headersJson(),
                command.payloadJson(),
                command.status(),
                command.retryCount(),
                command.nextRetryAt(),
                command.lockedBy(),
                command.lockedUntil(),
                null,
                null
        );

        try {
            int updated = mapper.update(row);
            if (updated == 0) {
                int inserted = mapper.insert(row);
                if (inserted != 1) {
                    throw new DbAccessException("tc_msg_send_queue insert affected rows != 1. affected=" + inserted);
                }
            }

            return mapper.findByTopicAndIdempotencyKey(command.topic(), command.idempotencyKey())
                    .orElseThrow(() -> new DbAccessException(
                            "tc_msg_send_queue upsert succeeded but row not found. key=" + command.topic() + "/" + command.idempotencyKey()
                    ));

        } catch (DuplicateKeyException e) {
            throw new DbDuplicateKeyException(
                    "tc_msg_send_queue upsert duplicate (topic, idempotency_key). key=" + command.topic() + "/" + command.idempotencyKey(),
                    e
            );
        } catch (DataAccessException e) {
            throw new DbAccessException(
                    "tc_msg_send_queue upsert failed. key=" + command.topic() + "/" + command.idempotencyKey(),
                    e
            );
        } catch (RuntimeException e) {
            throw new DbAccessException(
                    "tc_msg_send_queue upsert failed (unexpected). key=" + command.topic() + "/" + command.idempotencyKey(),
                    e
            );
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
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
            return mapper.findByMsgKey(msgKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_msg_send_queue findByMsgKey failed. msgKey=" + msgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_msg_send_queue findByMsgKey failed (unexpected). msgKey=" + msgKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
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
            return mapper.findByTopicAndIdempotencyKey(topic, idempotencyKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_msg_send_queue findByTopicAndIdempotencyKey failed. key=" + topic + "/" + idempotencyKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_msg_send_queue findByTopicAndIdempotencyKey failed (unexpected). key=" + topic + "/" + idempotencyKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param status DB MyBatis 계층 처리에 사용하는 입력 값
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
            return mapper.findAllByStatus(status, p.offset(), p.limit());
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_msg_send_queue findAllByStatus failed. status=" + status, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_msg_send_queue findAllByStatus failed (unexpected). status=" + status, e);
        }
    }

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
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
            mapper.deleteByMsgKey(msgKey);
        } catch (DataAccessException e) {
            throw new DbAccessException("tc_msg_send_queue deleteByMsgKey failed. msgKey=" + msgKey, e);
        } catch (RuntimeException e) {
            throw new DbAccessException("tc_msg_send_queue deleteByMsgKey failed (unexpected). msgKey=" + msgKey, e);
        }
    }

    
    /**
     * DB MyBatis 계층 입력/설정 유효성을 검증합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
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
     * DB MyBatis 계층 도메인 처리 로직을 수행합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB MyBatis 계층 처리 결과
     */
    private long resolveKey(UpsertTcMsgSendQueue command) {
        if (command.msgKey() != null) {
            return command.msgKey();
        }

        return mapper.findByTopicAndIdempotencyKey(command.topic(), command.idempotencyKey())
                .map(TcMsgSendQueue::msgKey)
                .orElse(0L);
    }
}
