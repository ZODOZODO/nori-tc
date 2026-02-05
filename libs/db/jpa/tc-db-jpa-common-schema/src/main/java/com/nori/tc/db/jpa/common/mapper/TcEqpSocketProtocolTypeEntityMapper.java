package com.nori.tc.db.jpa.common.mapper;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocketProtocolType;
import com.nori.tc.db.domain.eqp.TcEqpSocketProtocolType;
import com.nori.tc.db.jpa.common.entity.TcEqpSocketProtocolTypeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * tc_eqp_socket_protocol_type MapStruct 매퍼.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpSocketProtocolTypeEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpSocketProtocolType toDomain(TcEqpSocketProtocolTypeEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - socketProtocolType: PK이므로 Store에서 관리 (ignore)
     */
    @Mapping(target = "socketProtocolType", ignore = true)
    void updateEntity(UpsertTcEqpSocketProtocolType command, @MappingTarget TcEqpSocketProtocolTypeEntity entity);
}
