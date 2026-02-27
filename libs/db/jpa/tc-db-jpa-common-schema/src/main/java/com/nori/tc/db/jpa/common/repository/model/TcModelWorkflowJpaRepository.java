package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelWorkflowEntity;

public interface TcModelWorkflowJpaRepository extends JpaRepository<TcModelWorkflowEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param workflowName DB JPA 계층 처리에 사용하는 입력 값
     * @param messageName 처리할 원본 데이터
     * @return 조회 결과(Optional)
     */
    Optional<TcModelWorkflowEntity> findByModelVersionKeyAndWorkflowNameAndMessageName(
            long modelVersionKey,
            String workflowName,
            String messageName
    );
}
