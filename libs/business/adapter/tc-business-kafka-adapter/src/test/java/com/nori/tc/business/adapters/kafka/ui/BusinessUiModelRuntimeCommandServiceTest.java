package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.core.ui.BusinessUiTaskErrorCode;
import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.core.workflow.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.common.ui.task.pipeline.UiTaskReplyStatus;
import com.nori.tc.common.ui.task.pipeline.UiTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link BusinessUiModelRuntimeCommandService} 단위 테스트입니다.
 */
class BusinessUiModelRuntimeCommandServiceTest {

    @Test
    void shouldUpdateEqpBindingWhenModelKeyExistsInUiMessageJson() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_UPDATE",
                "EQP-01",
                "{\"modelKey\":101}"
        );

        final UiTaskResult result = service.handleEqpCreateOrUpdate(request);

        Assertions.assertEquals(UiTaskReplyStatus.PASS, result.status());
        Assertions.assertEquals(101L, runtimePort.bindings.get("EQP-01"));
    }

    @Test
    void shouldReturnFailWhenModelKeyMissingOnEqpUpdate() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_UPDATE",
                "EQP-01",
                "{\"notModelKey\":1}"
        );

        final UiTaskResult result = service.handleEqpCreateOrUpdate(request);

        Assertions.assertEquals(UiTaskReplyStatus.FAIL, result.status());
        Assertions.assertEquals(BusinessUiTaskErrorCode.MODEL_KEY_REQUIRED, result.errorCode());
    }

    @Test
    void shouldReloadModelRuntimeUsingExistingBindingWhenUiMessageHasNoModelKey() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        runtimePort.bindings.put("EQP-02", 200L);

        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_UPDATE_JARFILE",
                "EQP-02",
                null
        );

        final UiTaskResult result = service.handleEqpUpdateJarfile(request);

        Assertions.assertEquals(UiTaskReplyStatus.PASS, result.status());
        Assertions.assertTrue(runtimePort.reloadedModelKeys.contains(200L));
    }

    @Test
    void shouldReturnFailWhenBindingMissingOnJarfileUpdate() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_UPDATE_JARFILE",
                "EQP-03",
                null
        );

        final UiTaskResult result = service.handleEqpUpdateJarfile(request);

        Assertions.assertEquals(UiTaskReplyStatus.FAIL, result.status());
        Assertions.assertEquals(BusinessUiTaskErrorCode.MODEL_BINDING_NOT_FOUND, result.errorCode());
    }

    @Test
    void shouldReturnFailWhenPluginRuntimeReloadFailsOnJarfileUpdate() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        runtimePort.bindings.put("EQP-04", 400L);

        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        eqpId -> {
                            throw new IllegalStateException("plugin reload failed");
                        },
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_UPDATE_JARFILE",
                "EQP-04",
                null
        );

        final UiTaskResult result = service.handleEqpUpdateJarfile(request);

        Assertions.assertEquals(UiTaskReplyStatus.FAIL, result.status());
        Assertions.assertEquals(BusinessUiTaskErrorCode.WORKFLOW_PLUGIN_RELOAD_FAILED, result.errorCode());
        Assertions.assertTrue(runtimePort.reloadedModelKeys.contains(400L));
    }

    private static KafkaUiTaskMessage createRequest(
            final String eventType,
            final String eqpId,
            final String uiMessage
    ) {
        return new KafkaUiTaskMessage(
                new KafkaUiTaskMessage.KafkaUiTaskMetadata(
                        eventType,
                        "2026-02-13T00:00:00Z",
                        "UI-BACKEND",
                        "TRACE-UNIT-001"
                ),
                new KafkaUiTaskMessage.KafkaUiTaskData(
                        eqpId,
                        "SOCKET",
                        uiMessage
                )
        );
    }

    /**
     * model runtime cache 포트 테스트 더블입니다.
     */
    private static final class FakeRuntimeMutationPort implements BusinessModelRuntimeMutationPort {
        private final Map<String, Long> bindings = new LinkedHashMap<>();
        private final Set<Long> reloadedModelKeys = new LinkedHashSet<>();

        @Override
        public void reloadAll() {
            // 테스트에서는 사용하지 않습니다.
        }

        @Override
        public void reloadModelRuntime(final long modelKey) {
            reloadedModelKeys.add(modelKey);
        }

        @Override
        public void updateEqpBinding(final String eqpId, final long modelKey) {
            bindings.put(eqpId, modelKey);
        }

        @Override
        public BusinessModelRuntimeSnapshot currentSnapshot() {
            return BusinessModelRuntimeSnapshot.of(bindings, Map.of());
        }

        @Override
        public Optional<TcModelRuntime> findRuntimeByModelKey(final long modelKey) {
            return Optional.empty();
        }
    }
}


