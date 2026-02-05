package com.nori.tc.db.jpa.common.mapper.model;

import com.nori.tc.db.core.model.upsert.UpsertTcModelEventId;
import com.nori.tc.db.domain.model.TcModelEventId;
import com.nori.tc.db.jpa.common.entity.model.TcModelEventIdEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelEventIdEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelEventId toDomain(TcModelEventIdEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - updatedAt은 JPA Auditing이 관리하므로 무시
     * - eventKey는 IDENTITY PK이므로 무시
     */
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "eventKey", ignore = true)
    void updateEntity(UpsertTcModelEventId command, @MappingTarget TcModelEventIdEntity entity);
}
