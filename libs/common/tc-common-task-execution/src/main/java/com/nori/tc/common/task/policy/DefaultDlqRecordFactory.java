package com.nori.tc.common.task.policy;

import java.util.Objects;

/**
 * DLQ 레코드 기본 팩토리 구현입니다.
 *
 * <p>예외 메시지는 운영 로그/저장소 폭주를 방지하기 위해 길이 제한을 적용합니다.</p>
 */
public final class DefaultDlqRecordFactory implements DlqRecordFactory {

    /**
     * 예외 메시지 최대 길이입니다.
     */
    private final int maxExceptionMessageLength;

    /**
     * 기본 팩토리를 생성합니다.
     *
     * @param maxExceptionMessageLength 예외 메시지 최대 길이
     */
    public DefaultDlqRecordFactory(final int maxExceptionMessageLength) {
        if (maxExceptionMessageLength <= 0) {
            throw new IllegalArgumentException("maxExceptionMessageLength must be > 0");
        }
        this.maxExceptionMessageLength = maxExceptionMessageLength;
    }

    /**
     * 실패 컨텍스트를 표준 DLQ 레코드로 변환합니다.
     *
     * @param context 실패 컨텍스트
     * @param finalCategory 최종 확정 실패 카테고리
     * @return DLQ 레코드
     */
    @Override
    public DlqRecord create(final TaskFailureContext context, final TaskFailureCategory finalCategory) {
        Objects.requireNonNull(context, "context is null");
        Objects.requireNonNull(finalCategory, "finalCategory is null");

        final Throwable failure = context.failure();
        final String exceptionClass;
        final String exceptionMessage;

        if (failure == null) {
            exceptionClass = "N/A";
            exceptionMessage = "No exception payload";
        } else {
            exceptionClass = failure.getClass().getName();
            exceptionMessage = truncate(failure.getMessage(), maxExceptionMessageLength);
        }

        return new DlqRecord(
                context.sourceTopic(),
                context.sourcePartition(),
                context.sourceOffset(),
                context.eqpId(),
                context.messageType(),
                context.messageName(),
                finalCategory,
                exceptionClass,
                exceptionMessage,
                context.attempt(),
                context.payloadRef(),
                context.occurredAtEpochMs()
        );
    }

    private static String truncate(final String message, final int maxLength) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength) + "...";
    }
}

