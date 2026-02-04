package com.nori.tc.db.jpa.common.mapper;

import com.nori.tc.db.core.model.NewTcModel;
import com.nori.tc.db.core.model.UpdateTcModel;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.jpa.common.entity.TcModelEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModel toDomain(TcModelEntity entity);

    /**
     * NewTcModel(Create Command) -> Entity
     * - modelKey: DB 자동 생성이므로 매핑 제외 (또는 Command에 없음)
     * - createdAt, updatedAt: JPA 관리
     * - modelName, modelVersion: 팩토리에서 초기화하지만, Command 값으로 덮어써도 무방함
     */
    @Mapping(target = "modelKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromNew(NewTcModel command, @MappingTarget TcModelEntity entity);

    /**
     * UpdateTcModel(Update Command) -> Entity
     * - modelKey: PK 변경 불가 (ignore)
     * - createdAt, updatedAt: JPA 관리
     */
    @Mapping(target = "modelKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpdate(UpdateTcModel command, @MappingTarget TcModelEntity entity);
}