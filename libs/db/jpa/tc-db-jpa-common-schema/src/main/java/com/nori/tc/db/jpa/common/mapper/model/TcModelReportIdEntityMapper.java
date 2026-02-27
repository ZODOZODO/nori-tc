package com.nori.tc.db.jpa.common.mapper.model;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.model.upsert.UpsertTcModelReportId;
import com.nori.tc.db.domain.model.TcModelReportId;
import com.nori.tc.db.jpa.common.entity.model.TcModelReportIdEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelReportIdEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelReportId toDomain(TcModelReportIdEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - reportKey는 DB 생성(IDENTITY)이므로 무시
     * - updatedAt은 JPA Auditing이 관리하므로 무시
     * - modelVersionKey/reportId는 Unique Key이므로 변경 금지 (ignore)
     */
    @Mapping(target = "reportKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "modelVersionKey", ignore = true)
    @Mapping(target = "reportId", ignore = true)
    void updateEntity(UpsertTcModelReportId command, @MappingTarget TcModelReportIdEntity entity);
}
