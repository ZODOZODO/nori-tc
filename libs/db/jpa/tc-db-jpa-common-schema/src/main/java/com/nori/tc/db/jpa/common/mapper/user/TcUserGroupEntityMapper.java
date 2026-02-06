package com.nori.tc.db.jpa.common.mapper.user;

import com.nori.tc.db.core.user.upsert.UpsertTcUserGroup;
import com.nori.tc.db.domain.user.TcUserGroup;
import com.nori.tc.db.jpa.common.entity.user.TcUserGroupEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * tc_user_group Entity 매퍼.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcUserGroupEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcUserGroup toDomain(TcUserGroupEntity entity);

    /**
     * UpsertTcUserGroup -> Entity
     * - groupId: PK 변경 불가 (ignore)
     * - createdAt, updatedAt: JPA 관리
     * - isActive: Store에서 조건적으로 반영 (ignore)
     */
    @Mapping(target = "groupId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    void updateFromUpsert(UpsertTcUserGroup command, @MappingTarget TcUserGroupEntity entity);
}
