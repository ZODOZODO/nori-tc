package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.runtime.BusinessMessageType;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.db.domain.common.model.ProtocolType;

import java.util.Objects;

/**
 * 액션 디스패치용 메시지 타입입니다.
 *
 * <p>{@link com.nori.tc.business.domain.runtime.BusinessMessageType}와 달리
 * EQP 이벤트를 프로토콜 기준으로 다시 분해하기 위해 별도 enum을 사용합니다.</p>
 */
public enum BusinessWorkflowActionMessageType {
    SECS,
    SOCKET,
    MES;

    /**
     * 런타임 레코드와 모델 정보를 기반으로 액션 메시지 타입을 결정합니다.
     *
     * <p>규칙:</p>
     * <p>1) MES 이벤트는 항상 {@code MES}</p>
     * <p>2) EQP 이벤트는 model protocol이 HSMS면 {@code SECS}, 아니면 {@code SOCKET}</p>
     * <p>3) UI 이벤트는 액션 디스패치 대상이 아니므로 예외</p>
     *
     * @param record inbound record
     * @param modelRuntime model runtime
     * @return action message type
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
            return modelRuntime.protocolType() == ProtocolType.HSMS ? SECS : SOCKET;
        }
        throw new IllegalArgumentException("Unsupported messageType for workflow action dispatch: " + record.messageType());
    }
}



