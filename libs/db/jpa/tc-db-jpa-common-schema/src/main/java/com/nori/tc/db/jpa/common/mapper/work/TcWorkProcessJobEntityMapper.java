package com.nori.tc.db.jpa.common.mapper.work;

import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessJob;
import com.nori.tc.db.domain.work.TcWorkProcessJob;
import com.nori.tc.db.jpa.common.entity.work.TcWorkProcessJobEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * tc_work_processjob Entity-DTO 변환 매퍼.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcWorkProcessJobEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcWorkProcessJob toDomain(TcWorkProcessJobEntity entity);

    /**
     * UpsertTcWorkProcessJob -> Entity
     *
     * <p>
     * - processJobKey: PK 변경 불가 (ignore)
     * - createdAt/updatedAt: JPA 라이프사이클에서 관리 (ignore)
     * </p>
     */
    @Mapping(target = "processJobKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpsert(UpsertTcWorkProcessJob command, @MappingTarget TcWorkProcessJobEntity entity);
}
