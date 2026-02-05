package com.nori.tc.db.jpa.common.mapper;

import com.nori.tc.db.core.eqp.upsert.UpsertTcEqpSocket;
import com.nori.tc.db.domain.eqp.TcEqpSocket;
import com.nori.tc.db.jpa.common.entity.TcEqpSocketEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpSocketEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpSocket toDomain(TcEqpSocketEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - charset: Store에서 기본값(UTF-8) 로직 처리하므로 ignore
     * - eqpKey: PK
     * - createdAt, updatedAt: JPA 관리
     */
    @Mapping(target = "charset", ignore = true)
    @Mapping(target = "eqpKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpsertTcEqpSocket command, @MappingTarget TcEqpSocketEntity entity);
}
