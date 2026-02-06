package com.nori.tc.db.jpa.common.mapper.work;

import com.nori.tc.db.core.work.upsert.UpsertTcWorkControlJob;
import com.nori.tc.db.domain.work.TcWorkControlJob;
import com.nori.tc.db.jpa.common.entity.work.TcWorkControlJobEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * tc_work_controljob Entity-DTO 변환 매퍼.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcWorkControlJobEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcWorkControlJob toDomain(TcWorkControlJobEntity entity);

    /**
     * UpsertTcWorkControlJob -> Entity
     *
     * <p>
     * - controlJobKey: PK 변경 불가 (ignore)
     * - createdAt/updatedAt: JPA 라이프사이클에서 관리 (ignore)
     * </p>
     */
    @Mapping(target = "controlJobKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpsert(UpsertTcWorkControlJob command, @MappingTarget TcWorkControlJobEntity entity);
}
