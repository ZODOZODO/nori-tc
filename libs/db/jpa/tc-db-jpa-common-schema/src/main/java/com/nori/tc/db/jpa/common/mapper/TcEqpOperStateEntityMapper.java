package com.nori.tc.db.jpa.common.mapper;

import com.nori.tc.db.core.eqp.UpsertTcEqpOperState;
import com.nori.tc.db.domain.eqp.TcEqpOperState;
import com.nori.tc.db.jpa.common.entity.TcEqpOperStateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpOperStateEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcEqpOperState toDomain(TcEqpOperStateEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - sinceAt: Store 레벨에서 비즈니스 로직(유지/갱신) 처리하므로 ignore
     * - eqpId: PK 변경 불가
     * - updatedAt: JPA Auditing 관리
     */
    @Mapping(target = "sinceAt", ignore = true)
    @Mapping(target = "eqpId", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpsertTcEqpOperState command, @MappingTarget TcEqpOperStateEntity entity);
}