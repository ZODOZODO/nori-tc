package com.nori.tc.db.jpa.common.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.user.TcUserGroupMemberEntity;

/**
 * tc_user_group_member Spring Data JPA Repository.
 */
public interface TcUserGroupMemberJpaRepository extends JpaRepository<TcUserGroupMemberEntity, Long> {

    Optional<TcUserGroupMemberEntity> findByUserPkAndGroupId(long userPk, long groupId);

    void deleteByUserPkAndGroupId(long userPk, long groupId);
}
