package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelEventIdEntity;

/**
 * tc_model_eventid Repository
 */
public interface TcModelEventIdJpaRepository extends JpaRepository<TcModelEventIdEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param eventId 처리할 이벤트 정보
     * @return 조회 결과(Optional)
     */
    Optional<TcModelEventIdEntity> findByModelKeyAndEventId(Long modelKey, String eventId);
}
