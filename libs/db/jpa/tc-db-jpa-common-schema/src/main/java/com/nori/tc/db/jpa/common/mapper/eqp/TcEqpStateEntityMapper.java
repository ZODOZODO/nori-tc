package com.nori.tc.db.jpa.common.mapper.eqp;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpState;
import com.nori.tc.db.domain.eqp.TcEqpState;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpStateEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpStateEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpState toDomain(TcEqpStateEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - eqpKey: PK 변경 불가
     * - updatedAt: JPA Auditing 관리
     */
    @Mapping(target = "eqpKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpsertTcEqpState command, @MappingTarget TcEqpStateEntity entity);
}
