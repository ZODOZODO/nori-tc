package com.nori.tc.db.jpa.common.repository.eqp;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nori.tc.db.jpa.common.entity.eqp.TcEqpParamEntity;

public interface TcEqpParamJpaRepository extends JpaRepository<TcEqpParamEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param eqpKey 설비 식별 정보
     * @param paramName DB JPA 계층 처리에 사용하는 입력 값
     * @param paramVersion DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcEqpParamEntity> findByEqpKeyAndParamNameAndParamVersion(
            long eqpKey,
            String paramName,
            String paramVersion
    );

    /**
     * 특정 설비(eqp_key)와 버전(param_version)의 파라미터 전체를 조회합니다.
     *
     * @param eqpKey 설비 식별 키
     * @param paramVersion 파라미터 버전
     * @return 해당 설비/버전의 파라미터 목록
     */
    List<TcEqpParamEntity> findAllByEqpKeyAndParamVersion(long eqpKey, String paramVersion);

    /**
     * 특정 설비(eqp_key)와 버전(param_version)의 파라미터 존재 여부를 확인합니다.
     *
     * @param eqpKey 설비 식별 키
     * @param paramVersion 파라미터 버전
     * @return 1건 이상 존재하면 true
     */
    boolean existsByEqpKeyAndParamVersion(long eqpKey, String paramVersion);

    /**
     * 특정 설비(eqp_key)와 버전(param_version)의 파라미터 전체를 삭제합니다.
     *
     * <p>체크인 후 EDIT 버전 정리에 사용합니다.</p>
     *
     * @param eqpKey 설비 식별 키
     * @param paramVersion 삭제할 파라미터 버전
     * @return 삭제된 행 수
     */
    @Modifying
    @Query("DELETE FROM TcEqpParamEntity e WHERE e.eqpKey = :eqpKey AND e.paramVersion = :paramVersion")
    int deleteAllByEqpKeyAndParamVersion(
            @Param("eqpKey") long eqpKey,
            @Param("paramVersion") String paramVersion
    );
}
