package com.nori.tc.db.jpa.common.mapper.model;

import com.nori.tc.db.core.model.NewTcModelWorkflow;
import com.nori.tc.db.core.model.UpdateTcModelWorkflow;
import com.nori.tc.db.domain.model.TcModelWorkflow;
import com.nori.tc.db.jpa.common.entity.model.TcModelWorkflowEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcModelWorkflowEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcModelWorkflow toDomain(TcModelWorkflowEntity entity);

    /**
     * NewTcModelWorkflow(Create Command) -> Entity
     * - workflowKey: DB 자동 생성이므로 매핑 제외
     * - updatedAt: JPA 관리
     */
    @Mapping(target = "workflowKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromNew(NewTcModelWorkflow command, @MappingTarget TcModelWorkflowEntity entity);

    /**
     * UpdateTcModelWorkflow(Update Command) -> Entity
     * - workflowKey: PK 변경 불가 (ignore)
     * - updatedAt: JPA 관리
     */
    @Mapping(target = "workflowKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpdate(UpdateTcModelWorkflow command, @MappingTarget TcModelWorkflowEntity entity);
}
