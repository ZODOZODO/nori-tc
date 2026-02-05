package com.nori.tc.db.jpa.common.mapper.eqp;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpStateHist;
import com.nori.tc.db.domain.eqp.TcEqpStateHist;
import com.nori.tc.db.jpa.common.entity.eqp.TcEqpStateHistEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpStateHistEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpStateHist toDomain(TcEqpStateHistEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - stateHistKey는 IDENTITY 컬럼이므로 무시
     */
    @Mapping(target = "stateHistKey", ignore = true)
    void updateEntity(UpsertTcEqpStateHist command, @MappingTarget TcEqpStateHistEntity entity);
}
