package com.nori.tc.db.jpa.common.mapper.model;

import com.nori.tc.db.core.model.upsert.UpsertTcModel;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.jpa.common.entity.model.TcModelEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModel toDomain(TcModelEntity entity);

    /**
     * UpsertTcModel(Upsert Command) -> Entity
     * - modelKey: PK 변경 불가 (ignore)
     * - createdAt, updatedAt: JPA 관리
     */
    @Mapping(target = "modelKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateFromUpsert(UpsertTcModel command, @MappingTarget TcModelEntity entity);
}