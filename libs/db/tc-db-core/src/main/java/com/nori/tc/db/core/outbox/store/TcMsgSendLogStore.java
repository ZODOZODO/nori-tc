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

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcMsgSendLog upsert(UpsertTcMsgSendLog command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param msgKey 대상 키 값
     * @param attemptNo DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcMsgSendLog> findByMsgKeyAttemptNo(long msgKey, int attemptNo);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param msgKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcMsgSendLog> findAllByMsgKey(long msgKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param msgKey 대상 키 값
     * @param attemptNo DB Core 계층 처리에 사용하는 입력 값
     */
    void deleteByMsgKeyAttemptNo(long msgKey, int attemptNo);
}
