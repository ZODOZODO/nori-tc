package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.core.workflow.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskProcessorSpec;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskReplyStatus;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

/**
 * {@link BusinessUiTaskProcessorRegistry} 단위 테스트입니다.
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

        Assertions.assertTrue(registry.find("EQP_DELETE").isEmpty());
    }

    /**
     * 테스트용 no-op runtime mutation 포트입니다.
     */
    private static final class NoopRuntimeMutationPort implements BusinessModelRuntimeMutationPort {
        @Override
        public void reloadAll() {
            // no-op
        }

        @Override
        public void reloadModelRuntime(final long modelKey) {
            // no-op
        }

        @Override
        public void updateEqpBinding(final String eqpId, final long modelKey) {
            // no-op
        }

        @Override
        public BusinessModelRuntimeSnapshot currentSnapshot() {
            return BusinessModelRuntimeSnapshot.of(Map.of(), Map.of());
        }

        @Override
        public Optional<TcModelRuntime> findRuntimeByModelKey(final long modelKey) {
            return Optional.empty();
        }
    }
}



