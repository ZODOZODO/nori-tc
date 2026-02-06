package com.nori.tc.db.jpa.common.mapper.outbox;

import com.nori.tc.db.core.outbox.upsert.UpsertTcMsgSendLog;
import com.nori.tc.db.domain.outbox.TcMsgSendLog;
import com.nori.tc.db.jpa.common.entity.outbox.TcMsgSendLogEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcMsgSendLogEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcMsgSendLog toDomain(TcMsgSendLogEntity entity);

    /**
     * Command -> Entity (Write/Update)
     * - sendLogKey는 IDENTITY 컬럼이므로 무시
     * - msgKey/attemptNo는 조회용 키이므로 무시 (Store에서 별도로 처리)
     */
    @Mapping(target = "sendLogKey", ignore = true)
    @Mapping(target = "msgKey", ignore = true)
    @Mapping(target = "attemptNo", ignore = true)
    void updateEntity(UpsertTcMsgSendLog command, @MappingTarget TcMsgSendLogEntity entity);
}
