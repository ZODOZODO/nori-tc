package com.nori.tc.db.jpa.common.mapper.model;

import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.jpa.common.entity.model.TcModelMdfEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * tc_model_mdf Entity <-> Domain 매핑 정의 (MapStruct)
 *
 * <p>
 * - create/update 공통 규칙: PK와 updated_at은 JPA가 관리하므로 매핑에서 제외합니다.
 * - create/update 시 필요한 필드만 command에서 entity로 반영합니다.
 * </p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelMdfEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelMdf toDomain(TcModelMdfEntity entity);

    /**
     * UpsertTcModelMdf(Upsert Command) -> Entity
     * - mdfKey: PK 변경 불가 (ignore)
     * - updatedAt: JPA 관리
     */
    @Mapping(target = "mdfKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpsert(UpsertTcModelMdf command, @MappingTarget TcModelMdfEntity entity);
}