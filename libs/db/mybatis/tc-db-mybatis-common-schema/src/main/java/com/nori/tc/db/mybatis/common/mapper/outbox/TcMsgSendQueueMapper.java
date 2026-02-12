package com.nori.tc.db.mybatis.common.mapper.outbox;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.common.outbox.TcMsgSendStatus;
import com.nori.tc.db.domain.outbox.TcMsgSendQueue;

/**
 * tc_msg_send_queue Mapper (FIX)
 *
 * <p>
 * - Unique: (topic, idempotency_key)
 * - PK: msg_key (identity)
 * </p>
 */
public interface TcMsgSendQueueMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param queue DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("q") TcMsgSendQueue queue);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param queue DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("q") TcMsgSendQueue queue);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param msgKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcMsgSendQueue> findByMsgKey(@Param("msgKey") long msgKey);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param topic Kafka 토픽 이름
     * @param idempotencyKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcMsgSendQueue> findByTopicAndIdempotencyKey(
            @Param("topic") String topic,
            @Param("idempotencyKey") String idempotencyKey
    );

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param status DB MyBatis 계층 처리에 사용하는 입력 값
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcMsgSendQueue> findAllByStatus(
            @Param("status") TcMsgSendStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param msgKey 대상 키 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByMsgKey(@Param("msgKey") long msgKey);
}
