package com.nori.tc.comm.domain.dlq;

/**
 * DLQ 사유 코드(표준) - Shared Kernel
 *
 * 목적
 * - 운영자가 "왜 DLQ로 갔는지"를 코드 레벨로 빠르게 분류/집계하기 위함입니다.
 * - 사유 코드는 대시보드/알람 조건으로 쓰이므로, 가능한 한 안정적으로 유지하십시오.
 */
public enum DlqReasonCode {

    /**
     * 디코딩 후 raw bytes 크기가 제한(예: 256KB)을 초과한 경우
     * - 폭주/비정상 payload 방어
     */
    PAYLOAD_TOO_LARGE,

    /**
     * base64 문자열이 깨졌거나 디코딩 불가인 경우
     */
    BASE64_DECODE_FAIL,

    /**
     * 필수 필드 누락 등 레코드/입력이 불완전한 경우
     */
    INVALID_INPUT,

    /**
     * eqpId 미등록/매핑 불가 등으로 처리할 수 없는 경우
     */
    UNKNOWN_EQUIPMENT,

    /**
     * eqp별 inboundQueue가 포화되어 적재할 수 없는 경우
     * - 통합 gateway에서는 "유실 대신 DLQ/격리"로 처리하는 정책이 필요합니다.
     */
    INBOUND_QUEUE_OVERFLOW,

    /**
     * reassembly buffer가 상한을 초과했거나, 프레임 추출이 불가능한 상태로 누적된 경우
     */
    REASSEMBLY_OVERFLOW,

    /**
     * 프레이밍(HSMS 프레임 추출 / SOCKET 프레임 추출) 단계에서 영구 오류가 발생한 경우
     */
    FRAMING_FAILED,

    /**
     * 파싱/변환(SECS-II decode, socketType 파싱 등) 단계에서 영구 오류가 발생한 경우
     */
    PARSING_FAILED,

    /**
     * 라우팅 정책(OUTBOX vs DIRECT_KAFKA) 적용 중 영구 오류가 발생한 경우
     */
    ROUTING_FAILED,

    /**
     * 발행 단계(OUTBOX insert / Kafka send)에서 영구 오류로 판단된 경우
     */
    PUBLISH_FAILED
}
