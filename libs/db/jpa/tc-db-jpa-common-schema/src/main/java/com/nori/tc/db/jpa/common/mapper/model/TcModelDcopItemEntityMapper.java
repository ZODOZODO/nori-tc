package com.nori.tc.db.jpa.common.mapper.model;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.model.upsert.UpsertTcModelDcopItem;
import com.nori.tc.db.domain.model.TcModelDcopItem;
import com.nori.tc.db.jpa.common.entity.model.TcModelDcopItemEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelDcopItemEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelDcopItem toDomain(TcModelDcopItemEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - dcopItemKey는 DB 생성(IDENTITY)이므로 무시
     * - updatedAt은 JPA Auditing이 관리하므로 무시
     * - modelVersionKey/dcopItemName은 Unique Key이므로 변경 금지 (ignore)
     */
    @Mapping(target = "dcopItemKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "modelVersionKey", ignore = true)
    @Mapping(target = "dcopItemName", ignore = true)
    void updateEntity(UpsertTcModelDcopItem command, @MappingTarget TcModelDcopItemEntity entity);
}
