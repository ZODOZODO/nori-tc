package com.nori.tc.db.jpa.common.mapper.work;

import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrierSlot;
import com.nori.tc.db.domain.work.TcWorkCarrierSlot;
import com.nori.tc.db.jpa.common.entity.work.TcWorkCarrierSlotEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcWorkCarrierSlotEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcWorkCarrierSlot toDomain(TcWorkCarrierSlotEntity entity);

    /**
     * Command -> Entity (Write/Update)
     *
     * - carrierSlotKey: PK 변경 불가 (ignore)
     * - workCarrierKey/slotNo: Unique Business Key로 이미 설정되므로 ignore
     * - updatedAt: JPA Auditing이 관리하므로 ignore
     */
    @Mapping(target = "carrierSlotKey", ignore = true)
    @Mapping(target = "workCarrierKey", ignore = true)
    @Mapping(target = "slotNo", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpsertTcWorkCarrierSlot command, @MappingTarget TcWorkCarrierSlotEntity entity);
}
