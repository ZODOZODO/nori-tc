package com.nori.tc.db.jpa.common.mapper.work;

import com.nori.tc.db.core.work.upsert.UpsertTcWork;
import com.nori.tc.db.domain.work.TcWork;
import com.nori.tc.db.jpa.common.entity.work.TcWorkEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcWorkEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcWork toDomain(TcWorkEntity entity);

    /**
     * UpsertTcWork(Upsert Command) -> Entity
     * - workKey: PK 변경 불가 (ignore)
     * - createdAt/updatedAt: JPA 관리
     */
    @Mapping(target = "workKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpsert(UpsertTcWork command, @MappingTarget TcWorkEntity entity);
}
