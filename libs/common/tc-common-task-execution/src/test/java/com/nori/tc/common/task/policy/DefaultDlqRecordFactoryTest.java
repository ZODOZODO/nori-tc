package com.nori.tc.common.task.policy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link DefaultDlqRecordFactory} 동작 검증 테스트입니다.
 */
class DefaultDlqRecordFactoryTest {

    /**
     * 긴 예외 메시지가 최대 길이로 절단되는지 검증합니다.
     */
    @Test
    void shouldTruncateExceptionMessageWhenTooLong() {
        final DefaultDlqRecordFactory factory = new DefaultDlqRecordFactory(10);
        final TaskFailureContext context = new TaskFailureContext(
                "tc.ui.events",
                1,
                200L,
                "EQP-01",
                "UI",
                "EQP_UPDATE",
                3,
                "payload://ui/200",
                TaskFailureCategory.ACTION_EXEC,
                new RuntimeException("12345678901234567890"),
                false,
                System.currentTimeMillis()
        );

        final DlqRecord record = factory.create(context, TaskFailureCategory.ACTION_EXEC);

        Assertions.assertEquals("payload://ui/200", record.payloadRef());
        Assertions.assertTrue(record.exceptionMessage().endsWith("..."));
    }
}

