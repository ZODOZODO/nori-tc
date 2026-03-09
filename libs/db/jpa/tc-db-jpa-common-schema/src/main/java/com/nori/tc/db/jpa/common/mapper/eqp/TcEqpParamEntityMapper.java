package com.nori.tc.db.jpa.common.mapper.eqp;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParam;
import com.nori.tc.db.domain.eqp.TcEqpParam;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpParamEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpParamEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpParam toDomain(TcEqpParamEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - eqpParamKey는 DB 생성(IDENTITY)이므로 무시
     * - updatedAt은 JPA Auditing이 관리하므로 무시
     * - eqpKey/paramName/paramVersion은 Unique Key이므로 변경 금지 (ignore)
     */
    @Mapping(target = "eqpParamKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "eqpKey", ignore = true)
    @Mapping(target = "paramName", ignore = true)
    @Mapping(target = "paramVersion", ignore = true)
    void updateEntity(UpsertTcEqpParam command, @MappingTarget TcEqpParamEntity entity);
}
