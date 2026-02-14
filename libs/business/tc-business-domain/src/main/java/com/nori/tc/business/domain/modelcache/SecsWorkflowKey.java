package com.nori.tc.business.domain.modelcache;

/**
 * SECS workflow 인덱스 조회 키입니다.
 *
 * <p>SECS 매칭은 {@code messageName + eventId + transactionId} 조합으로 수행합니다.</p>
 */
public record SecsWorkflowKey(
        String messageName,
        String eventId,
        String transactionId
) {

    /**
     * 생성 시 문자열 정규화를 수행합니다.
     */
    public SecsWorkflowKey {
        messageName = normalizeRequired("messageName", messageName);
        eventId = normalizeNullable(eventId);
        transactionId = normalizeNullable(transactionId);
    }

    /**
     * 팩토리 메서드입니다.
     *
     * @param messageName message name(필수)
     * @param eventId event id(옵션)
     * @param transactionId transaction id(옵션)
     * @return 정규화된 키
     */
    public static SecsWorkflowKey of(
            final String messageName,
            final String eventId,
            final String transactionId
    ) {
        return new SecsWorkflowKey(messageName, eventId, transactionId);
    }

    private static String normalizeRequired(final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}

