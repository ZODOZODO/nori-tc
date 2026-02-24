package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * BusinessUiTaskProcessorRegistryTest ?대옒?ㅼ엯?덈떎.
 *
 * <p>?대떦 紐⑤뱢?먯꽌 怨듯넻 怨꾩빟怨??숈옉 寃쎄퀎瑜??뺤쓽?섎ŉ,
 * ?몄텧 怨꾩링?먯꽌 ?쇨????ъ슜??媛?ν븯?꾨줉 ?ㅺ퀎?섏뿀?듬땲??</p>
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
     * UTF-8 ?뺤떇?쇰줈 ?뺣━??二쇱꽍?낅땲??
     */
    private static final class NoopRuntimeMutationPort implements BusinessModelRuntimeMutationPort {
        /**
         * reloadAll 湲곕뒫???섑뻾?⑸땲??
         *
         */

        @Override
        public void reloadAll() {
            // no-op
        }

        /**
         * reloadModelRuntime 湲곕뒫???섑뻾?⑸땲??
         *
         * @param modelKey ?낅젰 媛?         */

        @Override
        public void reloadModelRuntime(final long modelKey) {
            // no-op
        }

        /**
         * updateEqpBinding 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpId ?낅젰 媛?         * @param modelKey ?낅젰 媛?         */

        @Override
        public void updateEqpBinding(final String eqpId, final long modelKey) {
            // no-op
        }

        /**
         * removeEqpBinding 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpId ?낅젰 媛?         * @return 泥섎━ 寃곌낵
         */
        @Override
        public Optional<Long> removeEqpBinding(final String eqpId) {
            return Optional.empty();
        }

        /**
         * currentSnapshot 湲곕뒫???섑뻾?⑸땲??
         *
         * @return 泥섎━ 寃곌낵
         */

        @Override
        public BusinessModelRuntimeSnapshot currentSnapshot() {
            return BusinessModelRuntimeSnapshot.of(Map.of(), Map.of());
        }

        /**
         * findRuntimeByModelKey 湲곕뒫???섑뻾?⑸땲??
         *
         * @param modelKey ?낅젰 媛?         * @return 泥섎━ 寃곌낵
         */

        @Override
        public Optional<TcModelRuntime> findRuntimeByModelKey(final long modelKey) {
            return Optional.empty();
        }
    }
}




