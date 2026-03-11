package com.nori.tc.business.core.workflow.api.action;

import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.db.domain.common.model.ProtocolType;

import java.util.Objects;

/**
 * 액션 디스패치에 사용하는 메시지 타입입니다.
 */
public enum BusinessWorkflowActionMessageType {
    SECS,
    SOCKET,
    MES;

    /**
     * 인바운드 레코드와 모델 프로토콜을 기준으로 액션 타입을 결정합니다.
     *
     * @param record 인바운드 레코드
     * @param modelRuntime 모델 런타임
     * @return 액션 메시지 타입
     */
    public static BusinessWorkflowActionMessageType from(
            final BusinessInboundRecord record,
            final TcModelRuntime modelRuntime
    ) {
        Objects.requireNonNull(record, "record is null");
        Objects.requireNonNull(modelRuntime, "modelRuntime is null");

        if (record.messageType() == BusinessMessageType.MES) {
            return MES;
        }
        if (record.messageType() == BusinessMessageType.EQP) {
            return modelRuntime.protocolType() == ProtocolType.SECS ? SECS : SOCKET;
        }
        throw new IllegalArgumentException("Unsupported messageType for workflow action dispatch: " + record.messageType());
    }
}