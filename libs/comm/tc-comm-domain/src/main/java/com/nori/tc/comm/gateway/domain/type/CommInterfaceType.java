package com.nori.tc.comm.gateway.domain.type;

/**
 * 설비 통신 인터페이스 타입
 *
 * 배경/목적
 * - 차세대 TC에서는 "protocolType" 대신 "comm_interface" / "comm_interface_type" 용어를 표준으로 사용합니다.
 * - 값은 HSMS / SOCKET 두 종류만 존재합니다.
 *
 * 사용 위치(예시)
 * - 통합 tc-comm-gateway의 처리 파이프라인 분기(HSMS 처리 vs SOCKET 처리)
 * - 운영 관측(로그/메트릭 태그)
 *
 * 설계 원칙
 * - DB/설정 등 외부 입력에서 문자열로 들어올 수 있으므로 방어적 파싱(fromText)을 제공합니다.
 * - 대소문자/공백 차이는 허용하되, 의미가 다른 값은 즉시 예외로 실패시켜 운영 사고를 줄입니다.
 */
public enum CommInterfaceType {
    HSMS,
    SOCKET;

    /**
     * 문자열을 CommInterfaceType으로 변환합니다.
     *
     * 허용 예)
     * - "HSMS", "hsms", "  HSMS  "
     * - "SOCKET", "socket"
     *
     * @param text 외부 입력 문자열
     * @return CommInterfaceType
     * @throws IllegalArgumentException null/empty/unknown 값인 경우
     */
    public static CommInterfaceType fromText(final String text) {
        // 변환 단계: 입력 데이터를 현재 컨텍스트에 맞는 구조로 조합합니다.
        if (text == null) {
            throw new IllegalArgumentException("commInterfaceType text is null");
        }

        final String normalized = text.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("commInterfaceType text is empty");
        }

        return switch (normalized) {
            case "HSMS" -> HSMS;
            case "SOCKET" -> SOCKET;
            default -> throw new IllegalArgumentException("Unknown commInterfaceType: " + text);
        };
    }
}
