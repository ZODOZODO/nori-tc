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
