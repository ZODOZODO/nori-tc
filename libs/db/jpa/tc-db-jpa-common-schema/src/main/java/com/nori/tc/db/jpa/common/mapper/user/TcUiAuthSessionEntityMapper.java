package com.nori.tc.db.jpa.common.mapper.user;

import com.nori.tc.db.core.user.upsert.UpsertTcUiAuthSession;
import com.nori.tc.db.domain.user.TcUiAuthSession;
import com.nori.tc.db.jpa.common.entity.user.TcUiAuthSessionEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * TcUiAuthSession Entity <-> Domain/Command 변환 매퍼 (MapStruct)
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcUiAuthSessionEntityMapper {

    /**
     * Entity -> Domain 변환 (Read)
     */
    TcUiAuthSession toDomain(TcUiAuthSessionEntity entity);

    /**
     * Command -> Entity 업데이트 (Write)
     * - @MappingTarget: 새로운 객체를 만들지 말고, 파라미터로 넘겨준 entity 객체의 필드를 수정해라.
     * - token은 PK이므로 변경하지 않음.
     */
    @Mapping(target = "token", ignore = true)
    void updateEntity(UpsertTcUiAuthSession command, @MappingTarget TcUiAuthSessionEntity entity);
}
