package com.nori.tc.comm.adapters.kafka.contract;

/**
 * Business -> Gateway 명령 수신용 Kafka 계약 모델입니다.
 *
 * <p>설계 원칙:</p>
 * <p>1) 모든 토픽 계약을 `metadata + data` 형태로 통일합니다.</p>
 * <p>2) `eventType`은 라우팅 키가 아니라 처리/관측(log, metric) 분류에 사용합니다.</p>
 * <p>3) SOCKET/HSMS를 하나의 계약으로 수용하되, 현재 구현은 SOCKET 중심으로 동작합니다.</p>
 *
 * <p>참고:</p>
 * <p>- record 자체에서는 최소 검증만 수행하고, 실제 유효성 검증/오류 분류는 상위 계층에서 처리합니다.</p>
 */
public record GatewayBusinessCommandMessage(
        GatewayBusinessCommandMetadata metadata,
        GatewayBusinessCommandData data
) {

    /**
     * 공통 메타데이터 블록입니다.
     *
     * <p>필드:</p>
     * <p>- eventType: 이벤트 종류(예: EQP_SEND_MESSAGE)</p>
     * <p>- timestamp: ISO-8601 문자열</p>
     * <p>- source: 발행 애플리케이션 식별자</p>
     * <p>- traceId: 요청 추적 식별자</p>
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
     * <p>필드 사용:</p>
     * <p>- eqpId/interfaceType/rawMessage: SOCKET 명령 처리 입력</p>
     * <p>- transactionId/secs2: HSMS 확장 입력(현재 TODO 경계)</p>
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
     * HSMS SECS-II 데이터 블록입니다.
     *
     * <p>현재 구현 범위에서는 HSMS 송신 로직이 완성되지 않았기 때문에,
     * 해당 구조는 계약 보존 및 추후 확장을 위한 모델로 유지합니다.</p>
     */
    public record GatewayBusinessCommandSecs2(
            String systemBytes,
            String eventId,
            String rawBody
    ) {
    }
}

