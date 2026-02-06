package com.nori.tc.db.jpa.common.mapper.user;

import com.nori.tc.db.core.user.upsert.UpsertTcUserInfo;
import com.nori.tc.db.domain.user.TcUserInfo;
import com.nori.tc.db.jpa.common.entity.user.TcUserInfoEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * TcUserInfo Entity <-> Domain/Command 변환 매퍼 (MapStruct)
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcUserInfoEntityMapper {

    /**
     * Entity -> Domain 변환 (Read)
     */
    TcUserInfo toDomain(TcUserInfoEntity entity);

    /**
     * Command -> Entity 업데이트 (Write)
     * - @MappingTarget: 새로운 객체를 만들지 말고, 파라미터로 넘겨준 entity 객체의 필드를 수정해라.
     * - created_at, updated_at은 JPA가 관리하므로 덮어쓰지 않음.
     * - userPk는 조회 키이므로 굳이 덮어쓸 필요 없음(ignore).
     * - createdBy는 최초 생성 시점에만 세팅되므로 업데이트에서 제외.
     */
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userPk", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntity(UpsertTcUserInfo command, @MappingTarget TcUserInfoEntity entity);
}
