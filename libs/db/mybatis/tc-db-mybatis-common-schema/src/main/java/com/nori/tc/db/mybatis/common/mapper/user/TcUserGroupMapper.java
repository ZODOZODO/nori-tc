package com.nori.tc.db.mybatis.common.mapper.user;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.user.TcUserGroup;

/**
 * tc_user_group Mapper (FIX)
 *
 * <p>
 * - 논리 키: group_code
 * - 목록 조회는 DB 페이징을 사용한다.
 * </p>
 */
public interface TcUserGroupMapper {

    int insert(@Param("g") TcUserGroup group);

    int update(@Param("g") TcUserGroup group);

    Optional<TcUserGroup> findByGroupId(@Param("groupId") long groupId);

    Optional<TcUserGroup> findByGroupCode(@Param("groupCode") String groupCode);

    List<TcUserGroup> findAll(
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByGroupId(@Param("groupId") long groupId);
}
