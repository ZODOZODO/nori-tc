package com.nori.tc.db.jpa.common.repository.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelSecsMessageEntity;

/**
 * tc_model_secs_message Repository
 */
public interface TcModelSecsMessageJpaRepository extends JpaRepository<TcModelSecsMessageEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @return 조회/처리 결과 목록
     */
    List<TcModelSecsMessageEntity> findByModelKey(long modelKey);

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelKey 대상 키 값
     * @param secsMsgName DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelSecsMessageEntity> findByModelKeyAndSecsMsgName(long modelKey, String secsMsgName);
}
