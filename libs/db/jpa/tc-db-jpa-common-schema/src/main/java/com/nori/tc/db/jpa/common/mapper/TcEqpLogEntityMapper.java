package com.nori.tc.db.jpa.common.mapper;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpLog;
import com.nori.tc.db.domain.eqp.TcEqpLog;
import com.nori.tc.db.jpa.common.entity.TcEqpLogEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpLogEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpLog toDomain(TcEqpLogEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - updatedAt은 JPA Auditing이 관리하므로 무시
     * - eqpKey는 조회용 키이므로 무시 (Store에서 별도로 처리하거나 이미 찾아온 객체임)
     */
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "eqpKey", ignore = true)
    void updateEntity(UpsertTcEqpLog command, @MappingTarget TcEqpLogEntity entity);
}
