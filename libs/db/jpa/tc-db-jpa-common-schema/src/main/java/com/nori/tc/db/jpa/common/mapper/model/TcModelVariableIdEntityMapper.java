package com.nori.tc.db.jpa.common.mapper.model;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.model.upsert.UpsertTcModelVariableId;
import com.nori.tc.db.domain.model.TcModelVariableId;
import com.nori.tc.db.jpa.common.entity.model.TcModelVariableIdEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelVariableIdEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelVariableId toDomain(TcModelVariableIdEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - variableKey는 DB 생성(IDENTITY)이므로 무시
     * - updatedAt은 JPA Auditing이 관리하므로 무시
     * - modelVersionKey/variableIdType/variableId는 Unique Key이므로 변경 금지 (ignore)
     */
    @Mapping(target = "variableKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "modelVersionKey", ignore = true)
    @Mapping(target = "variableIdType", ignore = true)
    @Mapping(target = "variableId", ignore = true)
    void updateEntity(UpsertTcModelVariableId command, @MappingTarget TcModelVariableIdEntity entity);
}
