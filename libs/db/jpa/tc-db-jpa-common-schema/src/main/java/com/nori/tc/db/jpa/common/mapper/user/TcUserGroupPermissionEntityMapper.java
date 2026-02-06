package com.nori.tc.db.jpa.common.mapper.user;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.user.upsert.UpsertTcUserGroupPermission;
import com.nori.tc.db.domain.user.TcUserGroupPermission;
import com.nori.tc.db.jpa.common.entity.user.TcUserGroupPermissionEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcUserGroupPermissionEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcUserGroupPermission toDomain(TcUserGroupPermissionEntity entity);

    /**
     * Command -> Entity (Write/Update)
     *
     * - ugpKey: PK 변경 불가 (ignore)
     * - groupId/permId: Unique Business Key로 이미 설정되므로 ignore
     * - grantedAt: 엔티티 라이프사이클에서 관리하므로 ignore
     */
    @Mapping(target = "ugpKey", ignore = true)
    @Mapping(target = "groupId", ignore = true)
    @Mapping(target = "permId", ignore = true)
    @Mapping(target = "grantedAt", ignore = true)
    void updateEntity(UpsertTcUserGroupPermission command, @MappingTarget TcUserGroupPermissionEntity entity);
}
