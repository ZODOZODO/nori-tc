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

    Optional<TcUiPermissionEntity> findByPermCode(String permCode);
}
