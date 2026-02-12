package com.nori.tc.db.jpa.common.repository.work;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkEntity;

public interface TcWorkJpaRepository extends JpaRepository<TcWorkEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param workId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkEntity> findByEqpKeyAndWorkId(long eqpKey, String workId);
}
