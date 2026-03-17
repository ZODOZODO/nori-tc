package com.nori.tc.db.jpa.common.mapper.model;

import com.nori.tc.db.core.model.upsert.UpsertTcModelMesMessage;
import com.nori.tc.db.domain.model.TcModelMesMessage;
import com.nori.tc.db.jpa.common.entity.model.TcModelMesMessageEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelMesMessageEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelMesMessage toDomain(TcModelMesMessageEntity entity);

    /**
     * UpsertTcModelMesMessage(Upsert Command) -> Entity
     * - mesMsgKey: PK 변경 불가 (ignore)
     * - updatedAt: JPA 관리
     */
    @Mapping(target = "mesMsgKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpsert(UpsertTcModelMesMessage command, @MappingTarget TcModelMesMessageEntity entity);
}
