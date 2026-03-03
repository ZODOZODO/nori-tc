package com.nori.tc.db.domain.redis.dlq;

import java.io.Serializable;
import java.util.Map;

/**
 * Gateway Redis(6379)에 저장되는 DLQ(Dead Letter Queue) 엔트리입니다.
 *
 * <p>공유 위치 이유:</p>
 * <p>이 클래스는 {@code tc-comm-gateway-redis-adapter}가 직렬화(write)하고,
 * {@code tc-ui-redis-adapter}가 역직렬화(read)합니다.
 * JDK 직렬화는 패키지명 + 클래스명이 완전히 동일해야 역직렬화가 가능하므로,
 * 두 어댑터가 공통으로 참조할 수 있는 {@code tc-db-domain}에 위치합니다.</p>
 *
 * <p>저장 키 패턴: {@code tc:comm:gateway:dlq:{dlqId}}</p>
 *
 * <p>직렬화 주의사항:</p>
 * <ul>
 *   <li>필드를 추가하거나 삭제하면 기존 Redis에 저장된 데이터를 읽지 못할 수 있습니다.</li>
 *   <li>호환성이 깨지는 변경이 필요한 경우 {@code serialVersionUID}를 반드시 갱신하세요.</li>
 * </ul>
 */
public class RedisDlqEntry implements Serializable {

    /**
     * JDK 직렬화 호환성 식별자입니다.
     * 필드 구조가 변경되어 하위 호환성이 깨지는 경우 이 값을 함께 변경해야 합니다.
     */
    private static final long serialVersionUID = 1L;

    /** DLQ 항목 고유 식별자입니다. */
    private String dlqId;

    /** 장애가 발생한 설비의 식별자입니다. */
    private String equipmentId;

    /** 요청 추적에 사용하는 트레이스 ID입니다. */
    private String traceId;

    /** 통신 인터페이스 유형입니다 (예: HSMS, SOCKET). */
    private String commInterfaceType;

    /** 소켓 유형입니다 (예: ACTIVE, PASSIVE). */
    private String socketType;

    /** 처리 단계입니다 (예: PARSING, DISPATCH). */
    private String stage;

    /** 장애 발생 사유 코드입니다. */
    private String reasonCode;

    /** 장애 발생 사유 상세 메시지입니다. */
    private String reasonMessage;

    /** 장애 발생 시각 (Unix 에포크, 밀리초)입니다. */
    private long occurredAt;

    /** 페이로드 원본 데이터를 참조하는 Redis 키입니다. */
    private String payloadRefKey;

    /** 페이로드 원본 바이트 길이입니다. */
    private long rawLen;

    /** Base64 인코딩된 페이로드 바이트 길이입니다. */
    private long b64Len;

    /** 추가 분류 태그 (key-value)입니다. */
    private Map<String, String> tags;

    /**
     * Redis TTL(초 단위)입니다.
     * null 이면 만료 없이 영구 보관합니다.
     */
    private Long ttlSeconds;

    /**
     * Redis 직렬화 프레임워크용 기본 생성자입니다.
     * 외부에서 직접 인스턴스를 생성하지 마세요.
     */
    protected RedisDlqEntry() {
    }

    /**
     * DLQ 엔트리를 생성합니다.
     *
     * @param dlqId             DLQ 항목 고유 식별자
     * @param equipmentId       장애 설비 식별자
     * @param traceId           요청 추적 ID
     * @param commInterfaceType 통신 인터페이스 유형
     * @param socketType        소켓 유형
     * @param stage             처리 단계
     * @param reasonCode        장애 사유 코드
     * @param reasonMessage     장애 사유 상세 메시지
     * @param occurredAt        장애 발생 시각 (Unix 에포크 밀리초)
     * @param payloadRefKey     페이로드 참조 Redis 키
     * @param rawLen            페이로드 원본 바이트 길이
     * @param b64Len            Base64 인코딩 바이트 길이
     * @param tags              추가 분류 태그
     * @param ttlSeconds        Redis TTL(초), null 이면 영구 보관
     */
    public RedisDlqEntry(
            final String dlqId,
            final String equipmentId,
            final String traceId,
            final String commInterfaceType,
            final String socketType,
            final String stage,
            final String reasonCode,
            final String reasonMessage,
            final long occurredAt,
            final String payloadRefKey,
            final long rawLen,
            final long b64Len,
            final Map<String, String> tags,
            final Long ttlSeconds
    ) {
        this.dlqId = dlqId;
        this.equipmentId = equipmentId;
        this.traceId = traceId;
        this.commInterfaceType = commInterfaceType;
        this.socketType = socketType;
        this.stage = stage;
        this.reasonCode = reasonCode;
        this.reasonMessage = reasonMessage;
        this.occurredAt = occurredAt;
        this.payloadRefKey = payloadRefKey;
        this.rawLen = rawLen;
        this.b64Len = b64Len;
        this.tags = tags;
        this.ttlSeconds = ttlSeconds;
    }

    public String getDlqId() {
        return dlqId;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCommInterfaceType() {
        return commInterfaceType;
    }

    public String getSocketType() {
        return socketType;
    }

    public String getStage() {
        return stage;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonMessage() {
        return reasonMessage;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public String getPayloadRefKey() {
        return payloadRefKey;
    }

    public long getRawLen() {
        return rawLen;
    }

    public long getB64Len() {
        return b64Len;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Long getTtlSeconds() {
        return ttlSeconds;
    }
}
