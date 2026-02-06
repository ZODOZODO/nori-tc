package com.nori.tc.db.jpa.common.mapper.work;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.work.upsert.UpsertTcWorkParam;
import com.nori.tc.db.domain.work.TcWorkParam;
import com.nori.tc.db.jpa.common.entity.work.TcWorkParamEntity;

/**
 * tc_work_param Entity <-> Domain 매퍼.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcWorkParamEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcWorkParam toDomain(TcWorkParamEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - workParamKey는 DB 생성(IDENTITY)이므로 무시
     * - updatedAt은 JPA Auditing이 관리하므로 무시
     * - workKey/paramName은 Unique Key이므로 변경 금지 (ignore)
     */
    @Mapping(target = "workParamKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "workKey", ignore = true)
    @Mapping(target = "paramName", ignore = true)
    void updateEntity(UpsertTcWorkParam command, @MappingTarget TcWorkParamEntity entity);
}
