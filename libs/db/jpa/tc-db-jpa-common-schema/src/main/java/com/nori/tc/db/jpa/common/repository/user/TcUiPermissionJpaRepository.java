package com.nori.tc.db.jpa.common.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.user.TcUiPermissionEntity;

/**
 * tc_ui_permission Repository
 *
 * - 기본 CRUD는 JpaRepository로 충분합니다.
 * - perm_code 기반 조회는 유니크 키를 활용합니다.
 */
public interface TcUiPermissionJpaRepository extends JpaRepository<TcUiPermissionEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permCode DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUiPermissionEntity> findByPermCode(String permCode);
}
