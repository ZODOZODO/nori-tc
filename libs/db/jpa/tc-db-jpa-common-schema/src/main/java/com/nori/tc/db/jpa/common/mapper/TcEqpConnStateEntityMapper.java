package com.nori.tc.db.jpa.common.mapper;

import com.nori.tc.db.core.eqp.UpsertTcEqpConnState;
import com.nori.tc.db.domain.eqp.TcEqpConnState;
import com.nori.tc.db.jpa.common.entity.TcEqpConnStateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpConnStateEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpConnState toDomain(TcEqpConnStateEntity entity);

    /**
     * Command -> Entity (Write/Update)
     *
     * 1. sinceAt: "null이면 기존 유지 or now()"라는 비즈니스 로직이 있으므로, Mapper가 덮어쓰지 않도록 ignore 처리.
     * 2. eqpId: PK이므로 변경 불가 (ignore).
     * 3. updatedAt: JPA Auditing이 관리하므로 ignore.
     */
    @Mapping(target = "sinceAt", ignore = true)
    @Mapping(target = "eqpId", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpsertTcEqpConnState command, @MappingTarget TcEqpConnStateEntity entity);
}