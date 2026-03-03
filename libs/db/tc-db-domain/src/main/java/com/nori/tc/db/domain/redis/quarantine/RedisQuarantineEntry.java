package com.nori.tc.db.domain.redis.quarantine;

import java.io.Serializable;
import java.time.Instant;

/**
 * Gateway Redis(6379)에 저장되는 Quarantine(격리) 엔트리입니다.
 *
 * <p>공유 위치 이유:</p>
 * <p>이 클래스는 {@code tc-comm-gateway-redis-adapter}가 직렬화(write)하고,
 * {@code tc-ui-redis-adapter}가 역직렬화(read)합니다.
 * JDK 직렬화는 패키지명 + 클래스명이 완전히 동일해야 역직렬화가 가능하므로,
 * 두 어댑터가 공통으로 참조할 수 있는 {@code tc-db-domain}에 위치합니다.</p>
 *
 * <p>역할:</p>
 * <p>특정 설비가 격리(차단) 상태임을 기록합니다.
 * UI는 이 데이터를 조회하여 격리 설비 현황을 확인하고 격리 해제를 결정합니다.</p>
 *
 * <p>저장 키 패턴: {@code tc:comm:gateway:quarantine:{equipmentId}}</p>
 *
 * <p>직렬화 주의사항:</p>
 * <ul>
 *   <li>필드를 추가하거나 삭제하면 기존 Redis에 저장된 데이터를 읽지 못할 수 있습니다.</li>
 *   <li>호환성이 깨지는 변경이 필요한 경우 {@code serialVersionUID}를 반드시 갱신하세요.</li>
 * </ul>
 */
public class RedisQuarantineEntry implements Serializable {

    /**
     * JDK 직렬화 호환성 식별자입니다.
     * 필드 구조가 변경되어 하위 호환성이 깨지는 경우 이 값을 함께 변경해야 합니다.
     */
    private static final long serialVersionUID = 1L;

    /** 격리된 설비의 식별자입니다. */
    private String equipmentId;

    /** 격리 사유 코드입니다. */
    private String reasonCode;

    /** 격리 사유 상세 메시지입니다. */
    private String reasonMessage;

    /** 격리 적용 시각입니다. */
    private Instant quarantinedAt;

    /**
     * Redis TTL(초 단위)입니다.
     * null 이면 만료 없이 영구 보관합니다.
     */
    private Long ttlSeconds;

    /**
     * Redis 직렬화 프레임워크용 기본 생성자입니다.
     * 외부에서 직접 인스턴스를 생성하지 마세요.
     */
    protected RedisQuarantineEntry() {
    }

    /**
     * Quarantine 엔트리를 생성합니다.
     *
     * @param equipmentId   격리된 설비 식별자
     * @param reasonCode    격리 사유 코드
     * @param reasonMessage 격리 사유 상세 메시지
     * @param quarantinedAt 격리 적용 시각
     * @param ttlSeconds    Redis TTL(초), null 이면 영구 보관
     */
    public RedisQuarantineEntry(
            final String equipmentId,
            final String reasonCode,
            final String reasonMessage,
            final Instant quarantinedAt,
            final Long ttlSeconds
    ) {
        this.equipmentId = equipmentId;
        this.reasonCode = reasonCode;
        this.reasonMessage = reasonMessage;
        this.quarantinedAt = quarantinedAt;
        this.ttlSeconds = ttlSeconds;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonMessage() {
        return reasonMessage;
    }

    public Instant getQuarantinedAt() {
        return quarantinedAt;
    }

    public Long getTtlSeconds() {
        return ttlSeconds;
    }
}
