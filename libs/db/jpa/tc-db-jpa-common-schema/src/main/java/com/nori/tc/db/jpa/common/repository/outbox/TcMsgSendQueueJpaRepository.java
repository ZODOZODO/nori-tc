package com.nori.tc.db.jpa.common.repository.outbox;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.outbox.TcMsgSendQueueEntity;

/**
 * tc_msg_send_queue Spring Data JPA Repository.
 */
public interface TcMsgSendQueueJpaRepository extends JpaRepository<TcMsgSendQueueEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param topic Kafka 토픽 이름
     * @param idempotencyKey 대상 키 값
     * @return 조회 결과(Optional)
     */
    Optional<TcMsgSendQueueEntity> findByTopicAndIdempotencyKey(String topic, String idempotencyKey);
}
