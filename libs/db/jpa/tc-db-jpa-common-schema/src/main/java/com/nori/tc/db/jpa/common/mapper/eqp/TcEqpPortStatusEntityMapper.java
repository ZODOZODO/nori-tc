package com.nori.tc.db.jpa.common.mapper.eqp;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpPortStatus;
import com.nori.tc.db.domain.eqp.TcEqpPortStatus;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpPortStatusEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpPortStatusEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpPortStatus toDomain(TcEqpPortStatusEntity entity);

    /**
     * Command -> Entity (Write/Update)
     *
     * - eqpPortStatusKey: PK 변경 불가 (ignore)
     * - eqpKey/portId: Unique Business Key로 이미 설정되므로 ignore
     * - updatedAt: JPA Auditing이 관리하므로 ignore
     */
    @Mapping(target = "eqpPortStatusKey", ignore = true)
    @Mapping(target = "eqpKey", ignore = true)
    @Mapping(target = "portId", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpsertTcEqpPortStatus command, @MappingTarget TcEqpPortStatusEntity entity);
}
