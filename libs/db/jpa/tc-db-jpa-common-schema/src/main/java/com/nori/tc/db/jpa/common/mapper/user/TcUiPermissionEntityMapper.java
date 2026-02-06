package com.nori.tc.db.jpa.common.mapper.user;

import com.nori.tc.db.core.user.upsert.UpsertTcUiPermission;
import com.nori.tc.db.domain.user.TcUiPermission;
import com.nori.tc.db.jpa.common.entity.user.TcUiPermissionEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcUiPermissionEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcUiPermission toDomain(TcUiPermissionEntity entity);

    /**
     * UpsertTcUiPermission(Upsert Command) -> Entity
     * - permId: PK 변경 불가 (ignore)
     * - createdAt, updatedAt: JPA 관리
     */
    @Mapping(target = "permId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateFromUpsert(UpsertTcUiPermission command, @MappingTarget TcUiPermissionEntity entity);
}
