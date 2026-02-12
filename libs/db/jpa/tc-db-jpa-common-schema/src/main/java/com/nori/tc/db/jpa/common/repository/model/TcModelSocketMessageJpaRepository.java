package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelSocketMessageEntity;

/**
 * tc_model_socket_message Repository
 */
public interface TcModelSocketMessageJpaRepository extends JpaRepository<TcModelSocketMessageEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param socketMsgName 통신 채널/세션 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcModelSocketMessageEntity> findByModelKeyAndSocketMsgName(long modelKey, String socketMsgName);
}
