package com.nori.tc.db.mybatis.common.mapper.user;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.user.TcUserGroupPermission;

/**
 * tc_user_group_permission Mapper (FIX)
 *
 * - Unique Key: (group_id, perm_id)
 * - ugp_key는 IDENTITY이므로 insert 후 재조회 방식 사용
 */
public interface TcUserGroupPermissionMapper {

    int insert(@Param("p") TcUserGroupPermission permission);

    int update(@Param("p") TcUserGroupPermission permission);

    Optional<TcUserGroupPermission> findByGroupIdPermId(@Param("groupId") long groupId, @Param("permId") long permId);

    List<TcUserGroupPermission> findAllByGroupId(@Param("groupId") long groupId, @Param("offset") int offset, @Param("limit") int limit);

    int deleteByGroupIdPermId(@Param("groupId") long groupId, @Param("permId") long permId);
}
