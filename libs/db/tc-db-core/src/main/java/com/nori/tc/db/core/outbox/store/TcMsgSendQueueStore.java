package com.nori.tc.db.core.outbox.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.outbox.upsert.UpsertTcMsgSendQueue;
import com.nori.tc.db.domain.common.outbox.TcMsgSendStatus;
import com.nori.tc.db.domain.outbox.TcMsgSendQueue;

/**
 * tc_msg_send_queue CRUD 인터페이스 (기술 중립).
 *
 * <p>
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 * </p>
 */
public interface TcMsgSendQueueStore {

    /**
     * 큐 메시지 upsert.
     *
     * <p>
     * - msgKey가 있으면 PK 기반으로 갱신을 시도한다.
     * - msgKey가 없으면 (topic, idempotency_key) 유니크 키를 기준으로
     *   존재 여부를 확인한 뒤 갱신/생성을 수행한다.
     * </p>
     *
     * @return upsert 후 상태의 TcMsgSendQueue
     */
    TcMsgSendQueue upsert(UpsertTcMsgSendQueue command);

    /**
     * PK(msg_key)로 단건 조회.
     */
    Optional<TcMsgSendQueue> findByMsgKey(long msgKey);

    /**
     * 유니크 키(topic, idempotency_key)로 단건 조회.
     */
    Optional<TcMsgSendQueue> findByTopicAndIdempotencyKey(String topic, String idempotencyKey);

    /**
     * 상태별 목록 조회.
     *
     * <p>
     * 페이징은 반드시 DB 레벨에서 처리해야 한다.
     * </p>
     */
    List<TcMsgSendQueue> findAllByStatus(TcMsgSendStatus status, PageRequest page);

    /**
     * PK(msg_key) 기준 삭제.
     */
    void deleteByMsgKey(long msgKey);
}
