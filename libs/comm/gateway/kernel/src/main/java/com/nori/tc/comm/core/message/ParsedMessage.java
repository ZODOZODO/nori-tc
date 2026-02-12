package com.nori.tc.comm.core.message;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.domain.type.CommInterfaceType;

import java.util.Map;

/**
 * 파싱/변환이 완료된 메시지(코어 표준 모델)
 *
 * 통합 tc-comm-gateway 파이프라인
 * - HSMS: (HSMS 프레임 추출 -> 세션 머신 -> SECS-II 디코딩) 후 이 모델로 정규화
 * - SOCKET: socketType별 파싱 후 이 모델로 정규화
 *
 * 주의(가독성/확장성 우선)
 * - body는 특정 타입(Map/JSON/Bytes)으로 고정하지 않고, 최소한의 공통 필드만 표준화합니다.
 * - 실제 Kafka payload/outbox payload 형태는 app/adapter에서 직렬화 전략을 선택합니다.
 *
 * 필드
 * - equipmentId       : 설비 ID
 * - traceId           : 추적 ULID
 * - commInterfaceType : HSMS | SOCKET
 * - socketType        : SOCKET인 경우에만 의미(없으면 null 가능)
 * - messageName       : 라우팅의 키
 * - occurredAtEpochMs : 설비로부터 관측된 시각 또는 수신 시각(정책에 따라)
 * - attributes        : 메타 정보(예: stream/function/msgId 등). null 대신 빈 맵 권장
 * - body              : 파싱 결과 본문(직렬화는 adapter에서). null 허용
 */
public record ParsedMessage(
        EquipmentId equipmentId,
        String traceId,
        CommInterfaceType commInterfaceType,
        String socketType,
        MessageName messageName,
        long occurredAtEpochMs,
        Map<String, String> attributes,
        Object body
) {
    public ParsedMessage {
        if (equipmentId == null) throw new IllegalArgumentException("equipmentId is required");
        if (traceId == null || traceId.isBlank()) throw new IllegalArgumentException("traceId is required");
        if (commInterfaceType == null) throw new IllegalArgumentException("commInterfaceType is required");
        if (messageName == null) throw new IllegalArgumentException("messageName is required");
        if (attributes == null) attributes = Map.of();
    }
}
