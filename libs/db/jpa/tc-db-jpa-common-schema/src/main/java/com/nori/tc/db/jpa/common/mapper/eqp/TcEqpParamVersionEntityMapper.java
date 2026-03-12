package com.nori.tc.db.jpa.common.mapper.eqp;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpParamVersion;
import com.nori.tc.db.domain.eqp.TcEqpParamVersion;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpParamVersionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * tc_eqp_param_version Entity/Domain 매퍼입니다.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpParamVersionEntityMapper {

    TcEqpParamVersion toDomain(TcEqpParamVersionEntity entity);

    @Mapping(target = "eqpParamVersionKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "eqpKey", ignore = true)
    @Mapping(target = "paramVersion", ignore = true)
    void updateEntity(UpsertTcEqpParamVersion command, @MappingTarget TcEqpParamVersionEntity entity);
}
