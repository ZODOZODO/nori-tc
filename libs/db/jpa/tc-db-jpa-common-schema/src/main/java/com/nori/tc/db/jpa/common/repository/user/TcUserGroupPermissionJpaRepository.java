package com.nori.tc.db.jpa.common.repository.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.user.TcUserGroupPermissionEntity;

/**
 * tc_user_group_permission Repository.
 *
 * <p>
 * - Unique Key는 (group_id, perm_id)이며, 해당 조합으로 단건 조회를 제공합니다.
 * - group_id 기준 목록 조회는 store 레이어에서 페이징으로 사용됩니다.
 * </p>
 */
public interface TcUserGroupPermissionJpaRepository extends JpaRepository<TcUserGroupPermissionEntity, Long> {

    Optional<TcUserGroupPermissionEntity> findByGroupIdAndPermId(Long groupId, Long permId);

    List<TcUserGroupPermissionEntity> findByGroupId(Long groupId);
}
