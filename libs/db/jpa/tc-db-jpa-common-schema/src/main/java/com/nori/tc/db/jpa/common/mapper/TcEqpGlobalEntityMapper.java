package com.nori.tc.db.jpa.common.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.eqp.UpsertTcEqpGlobal;
import com.nori.tc.db.domain.eqp.TcEqpGlobal;
import com.nori.tc.db.jpa.common.entity.TcEqpGlobalEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpGlobalEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpGlobal toDomain(TcEqpGlobalEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - eqpGlobalKey는 DB에서 생성되므로 무시
     * - eqpKey/paramName은 유니크 키이므로 변경하지 않도록 무시
     * - updatedAt은 JPA Auditing으로 처리되므로 무시
     */
    @Mapping(target = "eqpGlobalKey", ignore = true)
    @Mapping(target = "eqpKey", ignore = true)
    @Mapping(target = "paramName", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpsertTcEqpGlobal command, @MappingTarget TcEqpGlobalEntity entity);
}
