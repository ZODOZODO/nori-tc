package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * BusinessUiModelRuntimeCommandServiceTest ?대옒?ㅼ엯?덈떎.
 *
 * <p>?대떦 紐⑤뱢?먯꽌 怨듯넻 怨꾩빟怨??숈옉 寃쎄퀎瑜??뺤쓽?섎ŉ,
 * ?몄텧 怨꾩링?먯꽌 ?쇨????ъ슜??媛?ν븯?꾨줉 ?ㅺ퀎?섏뿀?듬땲??</p>
 */
class BusinessUiModelRuntimeCommandServiceTest {

    @Test
    void shouldUpdateEqpBindingWhenModelVersionKeyExistsInUiMessageJson() {
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
     */
    private static final class FakeRuntimeMutationPort implements BusinessModelRuntimeMutationPort {
        private final Map<String, Long> bindings = new LinkedHashMap<>();
        private final Set<Long> reloadedModelVersionKeys = new LinkedHashSet<>();

        /**
         * reloadAll 湲곕뒫???섑뻾?⑸땲??
         *
         */

        @Override
        public void reloadAll() {
            // ?뚯뒪???붾툝 援ы쁽?먯꽌??蹂꾨룄 ?숈옉 ?놁씠 臾댁떆?⑸땲??
        }

        /**
         * reloadModelRuntime 湲곕뒫???섑뻾?⑸땲??
         *
         * @param modelVersionKey ?낅젰 媛?         */

        @Override
        public void reloadModelRuntime(final long modelVersionKey) {
            reloadedModelVersionKeys.add(modelVersionKey);
        }

        /**
         * updateEqpBinding 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpId ?낅젰 媛?         * @param modelVersionKey ?낅젰 媛?         */

        @Override
        public void updateEqpBinding(final String eqpId, final long modelVersionKey) {
            bindings.put(eqpId, modelVersionKey);
        }

        /**
         * removeEqpBinding 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpId ?낅젰 媛?         * @return 泥섎━ 寃곌낵
         */
        @Override
        public Optional<Long> removeEqpBinding(final String eqpId) {
            final Long removed = bindings.remove(eqpId);
            return Optional.ofNullable(removed);
        }

        /**
         * currentSnapshot 湲곕뒫???섑뻾?⑸땲??
         *
         * @return 泥섎━ 寃곌낵
         */

        @Override
        public BusinessModelRuntimeSnapshot currentSnapshot() {
            return BusinessModelRuntimeSnapshot.of(bindings, Map.of());
        }

        /**
         * findRuntimeByModelVersionKey 湲곕뒫???섑뻾?⑸땲??
         *
         * @param modelVersionKey ?낅젰 媛?         * @return 泥섎━ 寃곌낵
         */

        @Override
        public Optional<TcModelRuntime> findRuntimeByModelVersionKey(final long modelVersionKey) {
            return Optional.empty();
        }
    }

    /**
     * ?뚮윭洹몄씤 ?고????쒓굅 ?몄텧 ?щ?瑜?寃利앺븯湲??꾪븳 ?뚯뒪???붾툝?낅땲??
     */
    private static final class TrackingPluginMutationPort implements BusinessWorkflowPluginRuntimeMutationPort {

        /**
         * 留덉?留됱쑝濡?remove ?붿껌??eqpId?낅땲??
         */
        private String lastRemovedEqpId;

        /**
         * reloadByEqpId 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpId ?낅젰 媛?         */
        @Override
        public void reloadByEqpId(final String eqpId) {
            // ???뚯뒪???붾툝?먯꽌??reload 寃쎈줈瑜??ъ슜?섏? ?딆뒿?덈떎.
        }

        /**
         * removeByEqpId 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpId ?낅젰 媛?         */
        @Override
        public void removeByEqpId(final String eqpId) {
            this.lastRemovedEqpId = eqpId;
        }
    }
}



