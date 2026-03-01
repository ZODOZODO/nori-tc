package com.nori.tc.db.jpa.common.mapper.model;

import com.nori.tc.db.core.model.upsert.UpsertTcModelMdf;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.jpa.common.entity.model.TcModelMdfEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * {@link TcModelMdfEntity}와 {@link TcModelMdf} 간 변환 규칙입니다.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelMdfEntityMapper {

    /**
     * Entity를 Domain으로 변환합니다.
     */
    TcModelMdf toDomain(TcModelMdfEntity entity);

    /**
     * Upsert 명령을 기존 Entity에 반영합니다.
     *
     * <p>
     * PK와 updatedAt은 JPA가 관리하므로 매핑 대상에서 제외합니다.
     * </p>
     */
    @Mapping(target = "mdfKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpsert(UpsertTcModelMdf command, @MappingTarget TcModelMdfEntity entity);
}
