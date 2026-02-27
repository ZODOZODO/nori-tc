package com.nori.tc.db.jpa.common.repository.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.model.TcModelMdfEntity;

/**
 * tc_model_mdf Repository
 *
 * <p>
 * - 기본 CRUD는 JpaRepository로 충분합니다.
 * - (model_version_key, mdf_name) 조합 조회용 메서드를 제공합니다.
 * </p>
 */
public interface TcModelMdfJpaRepository extends JpaRepository<TcModelMdfEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param modelVersionKey 대상 키 값
     * @param mdfName DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcModelMdfEntity> findByModelVersionKeyAndMdfName(Long modelVersionKey, String mdfName);

}
