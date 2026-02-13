package com.nori.tc.db.jpa.common.mapper.jar;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.nori.tc.db.core.jar.upsert.UpsertTcJarBusiness;
import com.nori.tc.db.domain.jar.TcJarBusiness;
import com.nori.tc.db.jpa.common.entity.jar.TcJarBusinessEntity;

/**
 * tc_jar_business Entity <-> Domain/Command 매퍼.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcJarBusinessEntityMapper {

    /**
     * Entity -> Domain 변환(Read).
     *
     * @param entity JPA 엔티티
     * @return 도메인 DTO
     */
    TcJarBusiness toDomain(TcJarBusinessEntity entity);

    /**
     * Command -> Entity 갱신(Write).
     *
     * 설계 의도:
     * - eqpKey는 Store에서 PK 기준으로 조회/신규 생성 시점에 결정하므로 ignore
     * - createdAt/updatedAt은 JPA lifecycle이 관리하므로 ignore
     * - createdBy는 생성 시점에만 Store가 세팅하므로 ignore
     *
     * @param command upsert 입력 값
     * @param entity 갱신 대상 엔티티
     */
    @Mapping(target = "eqpKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntity(UpsertTcJarBusiness command, @MappingTarget TcJarBusinessEntity entity);
}
