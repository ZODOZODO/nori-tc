package com.nori.tc.db.jpa.common.mapper;

import com.nori.tc.db.core.eqp.UpsertTcEqpHsms;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.jpa.common.entity.TcEqpHsmsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpHsmsEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpHsms toDomain(TcEqpHsmsEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - createdAt, updatedAt은 JPA가 관리하므로 무시
     * - eqpKey는 조회 키이므로 무시
     */
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "eqpKey", ignore = true)
    void updateEntity(UpsertTcEqpHsms command, @MappingTarget TcEqpHsmsEntity entity);
}
