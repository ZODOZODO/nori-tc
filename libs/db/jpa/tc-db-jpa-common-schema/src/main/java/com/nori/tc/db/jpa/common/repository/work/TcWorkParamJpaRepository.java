package com.nori.tc.db.jpa.common.repository.work;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkParamEntity;

/**
 * tc_work_param 전용 JPA Repository.
 *
 * <p>
 * - Unique(work_key, param_name) 기준으로 단건 조회를 지원한다.
 * - 파라미터 삭제는 기본 JpaRepository.deleteById를 사용한다.
 * </p>
 */
public interface TcWorkParamJpaRepository extends JpaRepository<TcWorkParamEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param paramName DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkParamEntity> findByWorkKeyAndParamName(
            long workKey,
            String paramName
    );
}
