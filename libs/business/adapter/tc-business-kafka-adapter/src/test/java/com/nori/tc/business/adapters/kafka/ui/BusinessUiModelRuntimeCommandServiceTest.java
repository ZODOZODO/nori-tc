package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.modelcache.BusinessModelParamMutationPort;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.core.ui.BusinessUiTaskErrorCode;
import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskReplyStatus;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * BusinessUiModelRuntimeCommandService 테스트입니다.
 *
 * <p>해당 모듈에서 외부 의존성 없이 단위 테스트를 수행하기 위해,
 * 내부 구현에서 인터페이스를 직접 사용할 수 있도록 작성하였습니다.</p>
 */
class BusinessUiModelRuntimeCommandServiceTest {

    @Test
    void shouldUpdateEqpBindingWhenModelVersionKeyExistsInUiMessageJson() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        new FakeParamMutationPort(),
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_UPDATE",
                "EQP-01",
                "{\"modelVersionKey\":101}"
        );

        final KafkaTaskResult result = service.handleEqpCreateOrUpdate(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.PASS, result.status());
        Assertions.assertEquals(101L, runtimePort.bindings.get("EQP-01"));
    }

    @Test
    void shouldReturnFailWhenModelVersionKeyMissingOnEqpUpdate() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        new FakeParamMutationPort(),
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_UPDATE",
                "EQP-01",
                "{\"notModelVersionKey\":1}"
        );

        final KafkaTaskResult result = service.handleEqpCreateOrUpdate(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.FAIL, result.status());
        Assertions.assertEquals(BusinessUiTaskErrorCode.MODEL_KEY_REQUIRED, result.errorCode());
    }

    @Test
    void shouldReloadModelRuntimeUsingExistingBindingWhenUiMessageHasNoModelVersionKey() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        runtimePort.bindings.put("EQP-02", 200L);

        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        new FakeParamMutationPort(),
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_UPDATE_JARFILE",
                "EQP-02",
                null
        );

        final KafkaTaskResult result = service.handleEqpUpdateJarfile(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.PASS, result.status());
        Assertions.assertTrue(runtimePort.reloadedModelVersionKeys.contains(200L));
    }

    @Test
    void shouldReturnFailWhenBindingMissingOnJarfileUpdate() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        new FakeParamMutationPort(),
                        BusinessWorkflowPluginRuntimeMutationPort.noop(),
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_UPDATE_JARFILE",
                "EQP-03",
                null
        );

        final KafkaTaskResult result = service.handleEqpUpdateJarfile(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.FAIL, result.status());
        Assertions.assertEquals(BusinessUiTaskErrorCode.MODEL_BINDING_NOT_FOUND, result.errorCode());
    }

    @Test
    void shouldReturnFailWhenPluginRuntimeReloadFailsOnJarfileUpdate() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        runtimePort.bindings.put("EQP-04", 400L);

        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        new FakeParamMutationPort(),
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

        final KafkaTaskResult result = service.handleEqpUpdateJarfile(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.FAIL, result.status());
        Assertions.assertEquals(BusinessUiTaskErrorCode.WORKFLOW_PLUGIN_RELOAD_FAILED, result.errorCode());
        Assertions.assertTrue(runtimePort.reloadedModelVersionKeys.contains(400L));
    }

    @Test
    void shouldDeleteBindingAndRemovePluginRuntimeOnEqpDelete() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        runtimePort.bindings.put("EQP-05", 500L);
        final TrackingPluginMutationPort pluginPort = new TrackingPluginMutationPort();
        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        new FakeParamMutationPort(),
                        pluginPort,
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_DELETE",
                "EQP-05",
                null
        );

        final KafkaTaskResult result = service.handleEqpDelete(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.PASS, result.status());
        Assertions.assertFalse(runtimePort.bindings.containsKey("EQP-05"));
        Assertions.assertEquals("EQP-05", pluginPort.lastRemovedEqpId);
    }

    @Test
    void shouldReturnFailWhenBindingMissingOnEqpDelete() {
        final FakeRuntimeMutationPort runtimePort = new FakeRuntimeMutationPort();
        final TrackingPluginMutationPort pluginPort = new TrackingPluginMutationPort();
        final BusinessUiModelRuntimeCommandService service =
                new BusinessUiModelRuntimeCommandService(
                        runtimePort,
                        new FakeParamMutationPort(),
                        pluginPort,
                        new ObjectMapper()
                );

        final KafkaUiTaskMessage request = createRequest(
                "EQP_DELETE",
                "EQP-06",
                null
        );

        final KafkaTaskResult result = service.handleEqpDelete(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.FAIL, result.status());
        Assertions.assertEquals(BusinessUiTaskErrorCode.MODEL_BINDING_NOT_FOUND, result.errorCode());
        Assertions.assertNull(pluginPort.lastRemovedEqpId);
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
     * BusinessModelRuntimeMutationPort 테스트용 fake 구현체입니다.
     */
    private static final class FakeRuntimeMutationPort implements BusinessModelRuntimeMutationPort {
        private final Map<String, Long> bindings = new LinkedHashMap<>();
        private final Set<Long> reloadedModelVersionKeys = new LinkedHashSet<>();

        @Override
        public void reloadAll() {
            // 테스트 목적상 아무 동작도 하지 않습니다.
        }

        @Override
        public void reloadModelRuntime(final long modelVersionKey) {
            reloadedModelVersionKeys.add(modelVersionKey);
        }

        @Override
        public void updateEqpBinding(final String eqpId, final long modelVersionKey) {
            bindings.put(eqpId, modelVersionKey);
        }

        @Override
        public Optional<Long> removeEqpBinding(final String eqpId) {
            final Long removed = bindings.remove(eqpId);
            return Optional.ofNullable(removed);
        }

        @Override
        public BusinessModelRuntimeSnapshot currentSnapshot() {
            return BusinessModelRuntimeSnapshot.of(bindings, Map.of(), Map.of());
        }

        @Override
        public Optional<TcModelRuntime> findRuntimeByModelVersionKey(final long modelVersionKey) {
            return Optional.empty();
        }
    }

    /**
     * BusinessModelParamMutationPort 테스트용 no-op 구현체입니다.
     */
    private static final class FakeParamMutationPort implements BusinessModelParamMutationPort {

        @Override
        public void reloadModelParams(final long modelVersionKey) {
            // 테스트 목적상 아무 동작도 하지 않습니다.
        }

        @Override
        public void reloadEqpParams(final long eqpKey) {
            // 테스트 목적상 아무 동작도 하지 않습니다.
        }
    }

    /**
     * 마지막으로 remove 호출된 eqpId를 추적하기 위한 테스트용 구현체입니다.
     */
    private static final class TrackingPluginMutationPort implements BusinessWorkflowPluginRuntimeMutationPort {

        /**
         * 마지막으로 remove 호출된 eqpId입니다.
         */
        private String lastRemovedEqpId;

        @Override
        public void reloadByEqpId(final String eqpId) {
            // 이 테스트에서는 reload 경로를 사용하지 않습니다.
        }

        @Override
        public void removeByEqpId(final String eqpId) {
            this.lastRemovedEqpId = eqpId;
        }
    }
}
