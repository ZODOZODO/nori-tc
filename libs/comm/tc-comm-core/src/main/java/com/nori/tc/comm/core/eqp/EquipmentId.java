package com.nori.tc.comm.core.eqp;

/**
 * 설비 식별자(Value Object)
 *
 * 목적
 * - eqpId(String)를 여기저기 흩뿌리지 않고 의미를 명확히 하기 위해 래핑합니다.
 * - 검증 규칙이 생기면(예: 길이 제한/패턴) 이곳에서 단일 지점으로 관리할 수 있습니다.
 */
public record EquipmentId(String value) {

    public EquipmentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
