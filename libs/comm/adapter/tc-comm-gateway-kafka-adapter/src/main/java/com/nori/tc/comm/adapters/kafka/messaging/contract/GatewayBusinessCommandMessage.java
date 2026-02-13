package com.nori.tc.comm.adapters.kafka.messaging.contract;

/**
 * Business -> Gateway 명령 수신용 Kafka 메시지 계약입니다.
 *
 * <p>요구사항 기준:
 * 1) tc.eqp.events 발행 구조(metadata + data)와 동일한 JSON 형태를 사용합니다.
 * 2) eventType 값은 라우팅 키가 아니라 운영/추적 로그 용도로만 사용합니다.
 * 3) SOCKET/HSMS를 하나의 계약으로 수용하되, 현재 구현 단계에서는 SOCKET 처리만 활성화합니다.</p>
 *
 * <p>주의:
 * - 외부 시스템 입력을 안정적으로 수용하기 위해 record 생성자에서 강한 예외 검증은 두지 않습니다.
 * - 실제 필수값 검증/오류 분류는 디스패처 계층에서 수행합니다.</p>
 */
public record GatewayBusinessCommandMessage(
        GatewayBusinessCommandMetadata metadata,
        GatewayBusinessCommandData data
) {

    /**
     * 공통 메타데이터 블록입니다.
     *
     * <p>예시:
     * - eventType: CHECK_REPLY, S6F11 등
     * - timestamp: ISO-8601 문자열
     * - source   : 발행 시스템 식별자
     * - traceId  : 분산 추적 식별자</p>
     */
    public record GatewayBusinessCommandMetadata(
            String eventType,
            String timestamp,
            String source,
            String traceId
    ) {
    }

    /**
     * 공통 데이터 블록입니다.
     *
     * <p>SOCKET/HSMS를 함께 표현하기 위해 필드를 통합했습니다.
     * - eqpId/interfaceType/rawMessage: SOCKET 처리 시 핵심 입력
     * - transactionId/secs2         : HSMS 확장 입력(TODO 단계)</p>
     */
    public record GatewayBusinessCommandData(
            String transactionId,
            String eqpId,
            String interfaceType,
            GatewayBusinessCommandSecs2 secs2,
            String rawMessage
    ) {
    }

    /**
     * HSMS SECS-II 세부 블록입니다.
     *
     * <p>현재 구현 범위에서는 HSMS 송신을 활성화하지 않으므로,
     * 수신/로그/향후 확장을 위한 계약 보존 목적의 구조입니다.</p>
     */
    public record GatewayBusinessCommandSecs2(
            String systemBytes,
            String eventId,
            String rawBody
    ) {
    }
}
