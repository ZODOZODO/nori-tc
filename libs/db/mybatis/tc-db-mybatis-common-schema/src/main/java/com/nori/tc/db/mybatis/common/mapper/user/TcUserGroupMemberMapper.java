package com.nori.tc.db.mybatis.common.mapper.user;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.user.TcUserGroupMember;

/**
 * tc_user_group_member Mapper.
 *
 * <p>
 * - Unique: (user_pk, group_id)
 * - PK: ugm_key (identity)
 * </p>
 */
public interface TcUserGroupMemberMapper {

    int insert(@Param("m") TcUserGroupMember member);

    int update(@Param("m") TcUserGroupMember member);

    Optional<TcUserGroupMember> findByUgmKey(@Param("ugmKey") long ugmKey);

    Optional<TcUserGroupMember> findByUserPkAndGroupId(
            @Param("userPk") long userPk,
            @Param("groupId") long groupId
    );

    List<TcUserGroupMember> findAllByUserPk(
            @Param("userPk") long userPk,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<TcUserGroupMember> findAllByGroupId(
            @Param("groupId") long groupId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByUgmKey(@Param("ugmKey") long ugmKey);

    int deleteByUserPkAndGroupId(
            @Param("userPk") long userPk,
            @Param("groupId") long groupId
    );
}
