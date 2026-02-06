package com.nori.tc.db.core.outbox.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.outbox.upsert.UpsertTcMsgSendLog;
import com.nori.tc.db.domain.outbox.TcMsgSendLog;

/**
 * tc_msg_send_log CRUD 인터페이스.
 *
 * <p>
 * - (msg_key, attempt_no) 조합을 논리적 키로 보고 upsert를 제공한다.
 * - 목록 조회는 msg_key 기준으로 페이징 처리한다.
 * </p>
 */
public interface TcMsgSendLogStore {

    TcMsgSendLog upsert(UpsertTcMsgSendLog command);

    Optional<TcMsgSendLog> findByMsgKeyAttemptNo(long msgKey, int attemptNo);

    List<TcMsgSendLog> findAllByMsgKey(long msgKey, PageRequest page);

    void deleteByMsgKeyAttemptNo(long msgKey, int attemptNo);
}
