package com.nori.tc.db.jpa.common.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.user.TcUserGroupEntity;

/**
 * tc_user_group JPA Repository.
 */
public interface TcUserGroupJpaRepository extends JpaRepository<TcUserGroupEntity, Long> {

    Optional<TcUserGroupEntity> findByGroupCode(String groupCode);
}
