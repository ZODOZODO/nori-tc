package com.nori.tc.db.jpa.common.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.user.TcUiAuthSessionEntity;

/**
 * tc_ui_auth_session Repository
 */
public interface TcUiAuthSessionJpaRepository extends JpaRepository<TcUiAuthSessionEntity, String> {
}
