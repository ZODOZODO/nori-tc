package com.nori.tc.common.task.execution.policy.dlq;

import com.nori.tc.common.task.execution.policy.types.DlqRecord;
import com.nori.tc.common.task.execution.policy.types.TaskFailureCategory;
import com.nori.tc.common.task.execution.policy.types.TaskFailureContext;

import java.util.Objects;

/**
 * 기본 DLQ 레코드 생성기입니다.
 *
 * <p>역할:</p>
 * <p>1) 실패 컨텍스트를 `DlqRecord` 계약으로 정규화</p>
 * <p>2) 예외 클래스/메시지 추출</p>
 * <p>3) 예외 메시지 길이 상한 적용</p>
 */
public final class TaskDlqRecordFactory implements DlqRecordFactory {

    /**
     * 예외 메시지 최대 저장 길이입니다.
     */
    private final int maxExceptionMessageLength;

    /**
     * 생성기를 초기화합니다.
     *
     * @param maxExceptionMessageLength 예외 메시지 최대 길이
     */
    public TaskDlqRecordFactory(final int maxExceptionMessageLength) {
        if (maxExceptionMessageLength <= 0) {
            throw new IllegalArgumentException("maxExceptionMessageLength must be > 0");
        }
        this.maxExceptionMessageLength = maxExceptionMessageLength;
    }

    /**
     * 실패 컨텍스트를 DLQ 레코드로 변환합니다.
     *
     * @param context 실패 컨텍스트
     * @param finalCategory 최종 실패 카테고리
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

    /**
     * 메시지 길이를 상한으로 자릅니다.
     *
     * @param message 원본 메시지
     * @param maxLength 최대 허용 길이
     * @return 잘린 메시지(필요 시 말줄임표 포함)
     */
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
