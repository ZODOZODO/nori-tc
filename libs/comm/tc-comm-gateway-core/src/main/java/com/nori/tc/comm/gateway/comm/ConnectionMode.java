package com.nori.tc.comm.gateway.comm;

/**
 * 설비 기준 연결 모드(ACTIVE | PASSIVE)입니다.
 *
 * <p>중요: 이 enum은 <b>게이트웨이 관점</b>이 아니라 <b>설비 관점</b>입니다.</p>
 *
 * <p>ACTIVE 의미:</p>
 * <p>- 설비가 먼저 게이트웨이로 접속을 시도합니다.</p>
 * <p>- 게이트웨이는 서버(리스너) 역할을 수행합니다.</p>
 *
 * <p>PASSIVE 의미:</p>
 * <p>- 설비는 대기하고, 게이트웨이가 설비로 접속을 시도합니다.</p>
 * <p>- 게이트웨이는 클라이언트(아웃바운드 커넥터) 역할을 수행합니다.</p>
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
