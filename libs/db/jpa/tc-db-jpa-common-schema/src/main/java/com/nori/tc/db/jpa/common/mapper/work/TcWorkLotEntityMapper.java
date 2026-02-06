package com.nori.tc.db.jpa.common.mapper.work;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.work.upsert.UpsertTcWorkLot;
import com.nori.tc.db.domain.work.TcWorkLot;
import com.nori.tc.db.jpa.common.entity.work.TcWorkLotEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcWorkLotEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcWorkLot toDomain(TcWorkLotEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - workLotKey는 DB 생성(IDENTITY)이므로 무시
     * - updatedAt은 JPA Auditing이 관리하므로 무시
     * - workKey/lotId는 Unique Key이므로 변경 금지 (ignore)
     */
    @Mapping(target = "workLotKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "workKey", ignore = true)
    @Mapping(target = "lotId", ignore = true)
    void updateEntity(UpsertTcWorkLot command, @MappingTarget TcWorkLotEntity entity);
}
