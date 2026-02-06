package com.nori.tc.db.jpa.common.mapper.model;

import com.nori.tc.db.core.model.upsert.UpsertTcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.jpa.common.entity.model.TcModelSecsMessageEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelSecsMessageEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelSecsMessage toDomain(TcModelSecsMessageEntity entity);

    /**
     * UpsertTcModelSecsMessage(Upsert Command) -> Entity
     * - secsMsgKey: PK 변경 불가 (ignore)
     * - updatedAt: JPA 관리
     */
    @Mapping(target = "secsMsgKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpsert(UpsertTcModelSecsMessage command, @MappingTarget TcModelSecsMessageEntity entity);
}