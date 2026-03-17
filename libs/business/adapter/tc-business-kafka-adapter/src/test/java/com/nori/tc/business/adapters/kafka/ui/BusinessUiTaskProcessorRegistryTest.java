package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.modelcache.BusinessModelParamMutationPort;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskProcessorSpec;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskReplyStatus;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

/**
 * BusinessUiTaskProcessorRegistry 테스트입니다.
 *
 * <p>해당 모듈에서 외부 의존성 없이 단위 테스트를 수행하기 위해,
 * 내부 구현에서 인터페이스를 직접 사용할 수 있도록 작성하였습니다.</p>
 */
class BusinessUiTaskProcessorRegistryTest {

    @Test
    void shouldResolveProcessorForEqpUpdate() throws Exception {
        final BusinessUiTaskProcessorRegistry registry = new BusinessUiTaskProcessorRegistry(
                new BusinessUiModelRuntimeCommandService(
                        new NoopRuntimeMutationPort(),
                        new NoopParamMutationPort(),
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
                        "{\"modelVersionKey\":101}"
                )
        );
        Assertions.assertEquals(KafkaTaskReplyStatus.PASS, spec.processor().process(request).status());
    }

    @Test
    void shouldReturnEmptyWhenEventTypeNotSupported() {
        final BusinessUiTaskProcessorRegistry registry = new BusinessUiTaskProcessorRegistry(
                new BusinessUiModelRuntimeCommandService(
                        new NoopRuntimeMutationPort(),
                        new NoopParamMutationPort(),
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
                        new NoopParamMutationPort(),
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
     * BusinessModelRuntimeMutationPort 테스트용 no-op 구현체입니다.
     */
    private static final class NoopRuntimeMutationPort implements BusinessModelRuntimeMutationPort {

        @Override
        public void reloadAll() {
            // no-op
        }

        @Override
        public void reloadModelRuntime(final long modelVersionKey) {
            // no-op
        }

        @Override
        public void updateEqpBinding(final String eqpId, final long modelVersionKey) {
            // no-op
        }

        @Override
        public Optional<Long> removeEqpBinding(final String eqpId) {
            return Optional.empty();
        }

        @Override
        public BusinessModelRuntimeSnapshot currentSnapshot() {
            return BusinessModelRuntimeSnapshot.of(Map.of(), Map.of(), Map.of());
        }

        @Override
        public Optional<TcModelRuntime> findRuntimeByModelVersionKey(final long modelVersionKey) {
            return Optional.empty();
        }
    }

    /**
     * BusinessModelParamMutationPort 테스트용 no-op 구현체입니다.
     */
    private static final class NoopParamMutationPort implements BusinessModelParamMutationPort {

        @Override
        public void reloadModelParams(final long modelVersionKey) {
            // no-op
        }

        @Override
        public void reloadEqpParams(final long eqpKey) {
            // no-op
        }
    }
}
