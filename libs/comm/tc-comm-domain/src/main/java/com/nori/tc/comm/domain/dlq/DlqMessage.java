package com.nori.tc.comm.domain.dlq;

import com.nori.tc.comm.domain.type.CommInterfaceType;

import java.util.Map;

/**
 * DLQ 메시지(표준 메타데이터) - Shared Kernel
 *
 * 통합 tc-comm-gateway 기준 DLQ 원칙(권장)
 * - "유실" 대신 "DLQ + 격리(Quarantine)"로 운영 사고를 줄입니다.
 * - payload 전체를 DLQ 레코드에 그대로 넣기보다는,
 *   payloadRefKey(외부 저장소 키)만 남기고 원본은 별도 저장(예: Redis/DB/Object Storage)하는 것이 유리합니다.
 *
 * 설계 의도
 * - domain(shared kernel) 모듈은 "저장소/전송 방식"을 모릅니다.
 * - 따라서 DLQ 메시지는 "표준 메타"만 정의하고, 실제 저장/발행은 tc-comm-core/app(어댑터)에서 결정합니다.
 *
 * 필드 설명(운영 관측 관점)
 * - dlqId             : DLQ 항목 식별자(권장: ULID)
 * - eqpId             : 설비 비즈니스 키
 * - traceNo           : 추적용 ULID (파이프라인 전체에서 전파 권장)
 * - commInterfaceType : HSMS|SOCKET
 * - socketType        : SOCKET인 경우에만 의미(예: socket_protocol_type 값)
 * - stage             : 어느 단계에서 실패했는지(예: "ENQUEUE", "REASSEMBLY", "FRAMING", "PARSING", "ROUTING", "PUBLISH")
 * - reasonCode        : 사유 코드(enum)
 * - reasonMessage     : 사람이 이해할 수 있는 간단한 메시지(너무 길게 쓰지 말 것)
 * - occurredAt        : 실패 발생 시각(epoch milli)
 * - payloadRefKey     : 원본 payload가 저장된 위치(키). 없으면 null 허용.
 * - rawLen            : raw bytes 길이(알려진 경우, 모르면 -1)
 * - b64Len            : base64 길이(알려진 경우, 모르면 -1)
 * - tags              : 운영/메트릭 태그(버전/정책 등). 없으면 빈 맵 권장.
 *
 * 참고
 * - record는 불변이며, 가독성이 좋고 DTO로 적합합니다.
 */
public record DlqMessage(
        String dlqId,
        String eqpId,
        String traceNo,
        CommInterfaceType commInterfaceType,
        String socketType,
        String stage,
        DlqReasonCode reasonCode,
        String reasonMessage,
        long occurredAt,
        String payloadRefKey,
        int rawLen,
        int b64Len,
        Map<String, String> tags
) {
    /**
     * tags가 null로 들어오는 실수를 줄이기 위해, 기본값 보정 생성자를 제공합니다.
     */
    public DlqMessage {
        if (dlqId == null || dlqId.isBlank()) {
            throw new IllegalArgumentException("dlqId is required");
        }
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        if (traceNo == null || traceNo.isBlank()) {
            throw new IllegalArgumentException("traceNo is required");
        }
        if (commInterfaceType == null) {
            throw new IllegalArgumentException("commInterfaceType is required");
        }
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("stage is required");
        }
        if (reasonCode == null) {
            throw new IllegalArgumentException("reasonCode is required");
        }
        if (reasonMessage == null) {
            throw new IllegalArgumentException("reasonMessage is required");
        }
        if (tags == null) {
            tags = Map.of(); // record compact constructor에서 재할당 가능
        }
    }

    /**
     * rawLen/b64Len의 기본값(모름)을 통일하기 위한 상수.
     */
    public static final int UNKNOWN_LENGTH = -1;

    /**
     * 권장되는 stage 문자열(오타 방지용).
     * - core 모듈에서 단계별로 일관되게 사용하십시오.
     */
    public static final String STAGE_ENQUEUE = "ENQUEUE";
    public static final String STAGE_REASSEMBLY = "REASSEMBLY";
    public static final String STAGE_FRAMING = "FRAMING";
    public static final String STAGE_PARSING = "PARSING";
    public static final String STAGE_ROUTING = "ROUTING";
    public static final String STAGE_PUBLISH = "PUBLISH";
}
