package com.nori.tc.db.jpa.common.mapper.work;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.work.upsert.UpsertTcWorkProcessjobLotMap;
import com.nori.tc.db.domain.work.TcWorkProcessjobLotMap;
import com.nori.tc.db.jpa.common.entity.work.TcWorkProcessjobLotMapEntity;

/**
 * tc_work_processjob_lot_map Entity-DTO 변환 매퍼.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcWorkProcessjobLotMapEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcWorkProcessjobLotMap toDomain(TcWorkProcessjobLotMapEntity entity);

    /**
     * UpsertTcWorkProcessjobLotMap -> Entity
     *
     * <p>
     * - pjLotMapKey: PK 변경 불가 (ignore)
     * - createdAt/updatedAt: JPA 라이프사이클에서 관리 (ignore)
     * </p>
     */
    @Mapping(target = "pjLotMapKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpsert(UpsertTcWorkProcessjobLotMap command, @MappingTarget TcWorkProcessjobLotMapEntity entity);
}
