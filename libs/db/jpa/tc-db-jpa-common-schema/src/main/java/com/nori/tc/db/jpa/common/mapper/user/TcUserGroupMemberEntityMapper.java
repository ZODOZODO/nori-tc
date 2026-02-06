package com.nori.tc.db.jpa.common.mapper.user;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.user.upsert.UpsertTcUserGroupMember;
import com.nori.tc.db.domain.user.TcUserGroupMember;
import com.nori.tc.db.jpa.common.entity.user.TcUserGroupMemberEntity;

/**
 * tc_user_group_member Entity-DTO 변환 매퍼.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcUserGroupMemberEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcUserGroupMember toDomain(TcUserGroupMemberEntity entity);

    /**
     * UpsertTcUserGroupMember -> Entity
     *
     * <p>
     * - ugmKey: PK 변경 불가 (ignore)
     * - grantedAt: null일 때 기존 값을 유지하기 위해 Store에서 별도 처리
     * </p>
     */
    @Mapping(target = "ugmKey", ignore = true)
    @Mapping(target = "grantedAt", ignore = true)
    void updateFromUpsert(UpsertTcUserGroupMember command, @MappingTarget TcUserGroupMemberEntity entity);
}
