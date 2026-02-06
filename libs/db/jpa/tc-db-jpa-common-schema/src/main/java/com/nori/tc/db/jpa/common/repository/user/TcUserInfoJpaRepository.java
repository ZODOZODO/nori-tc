package com.nori.tc.db.jpa.common.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.user.TcUserInfoEntity;

/**
 * tc_user_info Repository
 */
public interface TcUserInfoJpaRepository extends JpaRepository<TcUserInfoEntity, Long> {

    Optional<TcUserInfoEntity> findByUserIdNorm(String userIdNorm);

    Optional<TcUserInfoEntity> findByEmail(String email);

    void deleteByUserPk(Long userPk);
}
