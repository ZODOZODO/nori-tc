package com.nori.tc.db.jpa.common.mapper.work;

import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrier;
import com.nori.tc.db.domain.work.TcWorkCarrier;
import com.nori.tc.db.jpa.common.entity.work.TcWorkCarrierEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcWorkCarrierEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcWorkCarrier toDomain(TcWorkCarrierEntity entity);

    /**
     * Command -> Entity (Write/Update)
     *
     * <p>
     * - workCarrierKey: PK 변경 불가 (ignore)
     * - workKey/carrierId: Unique Business Key로 이미 설정되므로 ignore
     * - updatedAt: JPA Auditing이 관리하므로 ignore
     * </p>
     */
    @Mapping(target = "workCarrierKey", ignore = true)
    @Mapping(target = "workKey", ignore = true)
    @Mapping(target = "carrierId", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpsertTcWorkCarrier command, @MappingTarget TcWorkCarrierEntity entity);
}
