package com.nori.tc.db.jpa.common.mapper.model;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.model.upsert.UpsertTcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;
import com.nori.tc.db.jpa.common.entity.model.TcModelSocketMessageEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelSocketMessageEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelSocketMessage toDomain(TcModelSocketMessageEntity entity);

    /**
     * Command -> Entity (Write/Update)
     *
     * - socketMsgKey: PK 변경 불가 (ignore)
     * - modelVersionKey/socketMsgName: Unique Business Key로 이미 설정되므로 ignore
     * - updatedAt: JPA Auditing이 관리하므로 ignore
     */
    @Mapping(target = "socketMsgKey", ignore = true)
    @Mapping(target = "modelVersionKey", ignore = true)
    @Mapping(target = "socketMsgName", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpsertTcModelSocketMessage command, @MappingTarget TcModelSocketMessageEntity entity);
}
