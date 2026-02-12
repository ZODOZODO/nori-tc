package com.nori.tc.comm.gateway.comm;

/**
 * Equipment connection mode (ACTIVE | PASSIVE).
 *
 * - ACTIVE : Gateway connects to equipment (client).
 * - PASSIVE: Gateway listens and equipment connects (server).
 */
public enum ConnectionMode {
    ACTIVE,
    PASSIVE;

    /**
     * Parse connection mode text from DB/config.
     *
     * @param text input text (e.g. "ACTIVE", "PASSIVE")
     * @return ConnectionMode
     * @throws IllegalArgumentException when text is null/blank/unknown
     */
    public static ConnectionMode fromText(final String text) {
        // 변환 단계: 입력 데이터를 현재 컨텍스트에 맞는 구조로 조합합니다.
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
