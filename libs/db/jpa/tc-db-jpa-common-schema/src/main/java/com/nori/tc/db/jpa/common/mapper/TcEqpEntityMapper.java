package com.nori.tc.db.jpa.common.mapper;

import com.nori.tc.db.core.eqp.UpsertTcEqp;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.jpa.common.entity.TcEqpEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * TcEqp Entity <-> Domain/Command 변환 매퍼 (MapStruct)
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcEqpEntityMapper {

    /**
     * Entity -> Domain 변환 (Read)
     */
    TcEqp toDomain(TcEqpEntity entity);

    /**
     * Command -> Entity 업데이트 (Write)
     * - @MappingTarget: 새로운 객체를 만들지 말고, 파라미터로 넘겨준 entity 객체의 필드를 수정해라.
     * - created_at, updated_at은 JPA가 관리하므로 덮어쓰지 않음.
     * - eqpId는 조회 키이므로 굳이 덮어쓸 필요 없음(ignore).
     */
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "eqpId", ignore = true) 
    void updateEntity(UpsertTcEqp command, @MappingTarget TcEqpEntity entity);
}