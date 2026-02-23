package com.nori.tc.comm.gateway.comm;

/**
 * 게이트웨이 기준 연결 모드(ACTIVE | PASSIVE)입니다.
 *
 * <p>중요: 이 enum은 <b>게이트웨이 관점</b> 기준으로 해석합니다.</p>
 * <p>즉, DB의 {@code connection_mode} 값도 이 enum으로 읽히는 순간 게이트웨이 기준 의미로 사용됩니다.</p>
 *
 * <p>ACTIVE 의미(게이트웨이 ACTIVE):</p>
 * <p>- 게이트웨이가 먼저 설비로 아웃바운드 연결을 시도합니다.</p>
 * <p>- 게이트웨이는 클라이언트(아웃바운드 커넥터) 역할을 수행합니다.</p>
 *
 * <p>PASSIVE 의미(게이트웨이 PASSIVE):</p>
 * <p>- 게이트웨이가 수신 대기(listener)를 열고 설비 접속을 기다립니다.</p>
 * <p>- 게이트웨이는 서버(리스너) 역할을 수행합니다.</p>
 */
public enum ConnectionMode {
    ACTIVE,
    PASSIVE;

    /**
     * DB/설정 문자열을 {@link ConnectionMode}로 파싱합니다.
     *
     * <p>허용 값은 ACTIVE, PASSIVE(대소문자 무관)이며, 공백은 trim 처리합니다.</p>
     *
     * @param text 원본 문자열(예: "ACTIVE", "PASSIVE")
     * @return 파싱된 연결 모드
     * @throws IllegalArgumentException 값이 null/blank/미지원 문자열인 경우
     */
    public static ConnectionMode fromText(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("connectionMode is null");
        }

        final String normalized = text.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("connectionMode is empty");
        }

        return switch (normalized) {
            case "ACTIVE" -> ACTIVE;
            case "PASSIVE" -> PASSIVE;
            default -> throw new IllegalArgumentException("Unknown connectionMode: " + text);
        };
    }
}
