package com.nori.tc.db.jpa.common.mapper.outbox;

import com.nori.tc.db.core.outbox.upsert.UpsertTcMsgSendQueue;
import com.nori.tc.db.domain.outbox.TcMsgSendQueue;
import com.nori.tc.db.jpa.common.entity.outbox.TcMsgSendQueueEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * tc_msg_send_queue Entity-DTO 변환 매퍼.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TcMsgSendQueueEntityMapper {

    /**
     * Entity -> Domain (Read)
     */
    TcMsgSendQueue toDomain(TcMsgSendQueueEntity entity);

    /**
     * UpsertTcMsgSendQueue -> Entity
     *
     * <p>
     * - msgKey: PK 변경 불가 (ignore)
     * - createdAt/updatedAt: JPA 라이프사이클에서 관리 (ignore)
     * </p>
     */
    @Mapping(target = "msgKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromUpsert(UpsertTcMsgSendQueue command, @MappingTarget TcMsgSendQueueEntity entity);
}
