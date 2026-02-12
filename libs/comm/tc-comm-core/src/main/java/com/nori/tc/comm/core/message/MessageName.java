package com.nori.tc.comm.core.message;

/**
 * 메시지명(Value Object)
 *
 * 배경
 * - 라우팅 정책(OUTBOX vs DIRECT_KAFKA)에서 "메시지명"이 핵심 키가 됩니다.
 * - 문자열을 그대로 쓰면 오타/공백 등으로 운영 사고가 나기 쉬우므로 최소한의 정규화를 합니다.
 */
public record MessageName(String value) {

    public MessageName {
        if (value == null) {
            throw new IllegalArgumentException("messageName is required");
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("messageName is empty");
        }
        value = normalized;
    }

    
    /**
     * 통신 코어 모듈 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @return 통신 코어 모듈 처리 결과
     */
    @Override
    public String toString() {
        return value;
    }
}
