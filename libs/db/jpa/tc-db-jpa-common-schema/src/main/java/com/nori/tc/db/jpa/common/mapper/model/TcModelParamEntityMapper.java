package com.nori.tc.db.jpa.common.mapper.model;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.model.upsert.UpsertTcModelParam;
import com.nori.tc.db.domain.model.TcModelParam;
import com.nori.tc.db.jpa.common.entity.model.TcModelParamEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelParamEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelParam toDomain(TcModelParamEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - modelParamKey는 DB 생성(IDENTITY)이므로 무시
     * - updatedAt은 JPA Auditing이 관리하므로 무시
     * - modelKey/paramName은 Unique Key이므로 변경 금지 (ignore)
     */
    @Mapping(target = "modelParamKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "modelKey", ignore = true)
    @Mapping(target = "paramName", ignore = true)
    void updateEntity(UpsertTcModelParam command, @MappingTarget TcModelParamEntity entity);
}
