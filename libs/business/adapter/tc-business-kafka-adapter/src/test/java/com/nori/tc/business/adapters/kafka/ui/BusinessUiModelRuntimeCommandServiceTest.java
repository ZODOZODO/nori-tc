package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.core.ui.BusinessUiTaskErrorCode;
import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.core.workflow.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskReplyStatus;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * BusinessUiModelRuntimeCommandServiceTest 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
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

        final KafkaTaskResult result = service.handleEqpCreateOrUpdate(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.PASS, result.status());
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

        final KafkaTaskResult result = service.handleEqpCreateOrUpdate(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.FAIL, result.status());
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

        final KafkaTaskResult result = service.handleEqpUpdateJarfile(request);

        Assertions.assertEquals(KafkaTaskReplyStatus.PASS, result.status());
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
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private static final class FakeRuntimeMutationPort implements BusinessModelRuntimeMutationPort {
        private final Map<String, Long> bindings = new LinkedHashMap<>();
        private final Set<Long> reloadedModelKeys = new LinkedHashSet<>();

        /**
         * reloadAll 기능을 수행합니다.
         *
         */

        @Override
        public void reloadAll() {
            // 테스트 더블 구현에서는 별도 동작 없이 무시합니다.
        }

        /**
         * reloadModelRuntime 기능을 수행합니다.
         *
         * @param modelKey 입력 값
         */

        @Override
        public void reloadModelRuntime(final long modelKey) {
            reloadedModelKeys.add(modelKey);
        }

        /**
         * updateEqpBinding 기능을 수행합니다.
         *
         * @param eqpId 입력 값
         * @param modelKey 입력 값
         */

        @Override
        public void updateEqpBinding(final String eqpId, final long modelKey) {
            bindings.put(eqpId, modelKey);
        }

        /**
         * currentSnapshot 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        @Override
        public BusinessModelRuntimeSnapshot currentSnapshot() {
            return BusinessModelRuntimeSnapshot.of(bindings, Map.of());
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



