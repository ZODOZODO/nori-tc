package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.core.workflow.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskProcessorSpec;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskReplyStatus;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

/**
 * BusinessUiTaskProcessorRegistryTest 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */
class BusinessUiTaskProcessorRegistryTest {

    @Test
    void shouldResolveProcessorForEqpUpdate() throws Exception {
        final BusinessUiTaskProcessorRegistry registry = new BusinessUiTaskProcessorRegistry(
                new BusinessUiModelRuntimeCommandService(
                        new NoopRuntimeMutationPort(),
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                )
        );

        final Optional<KafkaTaskProcessorSpec<KafkaUiTaskMessage>> specOptional = registry.find("EQP_UPDATE");
        Assertions.assertTrue(specOptional.isPresent());

        final KafkaTaskProcessorSpec<KafkaUiTaskMessage> spec = specOptional.orElseThrow();
        Assertions.assertEquals("EQP_UPDATE", spec.eventType());
        Assertions.assertEquals("EQP_UPDATE_REP", spec.replyEventType());

        final KafkaUiTaskMessage request = new KafkaUiTaskMessage(
                new KafkaUiTaskMessage.KafkaUiTaskMetadata(
                        "EQP_UPDATE",
                        "2026-02-13T00:00:00Z",
                        "UI-BACKEND",
                        "TRACE-1"
                ),
                new KafkaUiTaskMessage.KafkaUiTaskData(
                        "EQP-TEST-01",
                        "SOCKET",
                        "{\"modelKey\":101}"
                )
        );
        Assertions.assertEquals(KafkaTaskReplyStatus.PASS, spec.processor().process(request).status());
    }

    @Test
    void shouldReturnEmptyWhenEventTypeNotSupported() {
        final BusinessUiTaskProcessorRegistry registry = new BusinessUiTaskProcessorRegistry(
                new BusinessUiModelRuntimeCommandService(
                        new NoopRuntimeMutationPort(),
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                )
        );

        Assertions.assertTrue(registry.find("UNKNOWN_EVENT").isEmpty());
    }

    @Test
    void shouldResolveProcessorForEqpDelete() throws Exception {
        final BusinessUiTaskProcessorRegistry registry = new BusinessUiTaskProcessorRegistry(
                new BusinessUiModelRuntimeCommandService(
                        new NoopRuntimeMutationPort(),
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                )
        );

        final Optional<KafkaTaskProcessorSpec<KafkaUiTaskMessage>> specOptional = registry.find("EQP_DELETE");
        Assertions.assertTrue(specOptional.isPresent());

        final KafkaTaskProcessorSpec<KafkaUiTaskMessage> spec = specOptional.orElseThrow();
        Assertions.assertEquals("EQP_DELETE", spec.eventType());
        Assertions.assertEquals("EQP_DELETE_REP", spec.replyEventType());

        final KafkaUiTaskMessage request = new KafkaUiTaskMessage(
                new KafkaUiTaskMessage.KafkaUiTaskMetadata(
                        "EQP_DELETE",
                        "2026-02-13T00:00:00Z",
                        "UI-BACKEND",
                        "TRACE-DEL-1"
                ),
                new KafkaUiTaskMessage.KafkaUiTaskData(
                        "EQP-DELETE-01",
                        "SOCKET",
                        null
                )
        );
        Assertions.assertEquals(KafkaTaskReplyStatus.FAIL, spec.processor().process(request).status());
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private static final class NoopRuntimeMutationPort implements BusinessModelRuntimeMutationPort {
        /**
         * reloadAll 기능을 수행합니다.
         *
         */

        @Override
        public void reloadAll() {
            // no-op
        }

        /**
         * reloadModelRuntime 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         */

        @Override
        public void reloadModelRuntime(final long modelKey) {
            // no-op
        }

        /**
         * updateEqpBinding 기능을 수행합니다.
         *
         * @param eqpId 입력 값
         * @param modelKey 입력 값
         */

        @Override
        public void updateEqpBinding(final String eqpId, final long modelKey) {
            // no-op
        }

        /**
         * removeEqpBinding 기능을 수행합니다.
         *
         * @param eqpId 입력 값
         * @return 처리 결과
         */
        @Override
        public Optional<Long> removeEqpBinding(final String eqpId) {
            return Optional.empty();
        }

        /**
         * currentSnapshot 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public BusinessModelRuntimeSnapshot currentSnapshot() {
            return BusinessModelRuntimeSnapshot.of(Map.of(), Map.of());
        }

        /**
         * findRuntimeByModelKey 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcModelRuntime> findRuntimeByModelKey(final long modelKey) {
            return Optional.empty();
        }
    }
}



