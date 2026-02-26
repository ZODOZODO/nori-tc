package com.nori.tc.common.task.execution.policy.dlq;

import com.nori.tc.common.task.execution.policy.types.DlqRecord;
import com.nori.tc.common.task.execution.policy.types.TaskFailureCategory;
import com.nori.tc.common.task.execution.policy.types.TaskFailureContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link TaskDlqRecordFactory} 테스트입니다.
 */
class TaskDlqRecordFactoryTest {

    /**
     * 예외 메시지 길이가 제한 길이를 넘으면 잘리는지 검증합니다.
     */
    @Test
    void shouldTruncateExceptionMessageWhenTooLong() {
        final TaskDlqRecordFactory factory = new TaskDlqRecordFactory(10);
        final TaskFailureContext context = new TaskFailureContext(
                "tc.ui.events.business",
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
