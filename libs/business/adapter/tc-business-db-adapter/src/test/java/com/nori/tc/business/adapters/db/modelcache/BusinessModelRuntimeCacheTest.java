package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.model.TcModelMdf;
import com.nori.tc.db.domain.model.TcModelWorkflow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@link BusinessModelRuntimeCache} 단위 테스트입니다.
 */
class BusinessModelRuntimeCacheTest {

    @Test
    void shouldReloadAllAndResolveRuntimeByEqpId() {
        final OffsetDateTime now = OffsetDateTime.now();

        final TcEqp eqp1 = new TcEqp(1L, "EQP-1", ProtocolType.HSMS, "ACTIVE", 0, "127.0.0.1", 5001, 101L, true, now, now, "SYSTEM", "SYSTEM");
        final TcEqp eqp2 = new TcEqp(2L, "EQP-2", ProtocolType.SOCKET, "ACTIVE", 1, "127.0.0.1", 5002, 102L, true, now, now, "SYSTEM", "SYSTEM");

        final TcModel model1 = new TcModel(101L, 101L, "MODEL-HSMS", "v1", ProtocolType.HSMS, ModelStatus.ACTIVE, "NORI", now, now, "SYSTEM", "SYSTEM");
        final TcModel model2 = new TcModel(102L, 102L, "MODEL-SOCKET", "v1", ProtocolType.SOCKET, ModelStatus.ACTIVE, "NORI", now, now, "SYSTEM", "SYSTEM");

        final Map<Long, List<TcModelWorkflow>> workflowsByModel = Map.of(
                101L, List.of(new TcModelWorkflow(1L, 101L, "WF-HSMS", "S6F11", "E1", "T1", null, "ACT-1", null, now)),
                102L, List.of(new TcModelWorkflow(2L, 102L, "WF-SOCKET", "SOCKET_IN", null, null, null, "ACT-2", null, now))
        );

        final List<TcModelMdf> mdfs = List.of(
                new TcModelMdf(
                        11L,
                        101L,
                        "MDF-101",
                        "<mdf><REQ_EQP>CMD=REQ EQPID={eqpId}</REQ_EQP></mdf>".getBytes(StandardCharsets.UTF_8),
                        now
                ),
                new TcModelMdf(
                        12L,
                        102L,
                        "MDF-102",
                        "<mdf><REQ_EQP>CMD=REQ EQPID={eqpId}</REQ_EQP></mdf>".getBytes(StandardCharsets.UTF_8),
                        now
                )
        );

        final BusinessModelCacheProperties properties = createValidModelCachePropertiesForTest(1);

        final BusinessModelRuntimeAssembler assembler = new BusinessModelRuntimeAssembler(
                new ModelCacheTestFixtures.InMemoryModelStore(List.of(model1, model2)),
                new ModelCacheTestFixtures.InMemoryWorkflowStore(workflowsByModel),
                new ModelCacheTestFixtures.InMemorySecsMessageStore(Map.of()),
                new ModelCacheTestFixtures.InMemorySocketMessageStore(Map.of()),
                new ModelCacheTestFixtures.InMemoryVariableIdStore(Map.of()),
                new ModelCacheTestFixtures.InMemoryMdfStore(mdfs),
                new BusinessMdfRuntimeParser(),
                properties
        );

        final BusinessModelRuntimeCache cache = new BusinessModelRuntimeCache(
                new ModelCacheTestFixtures.InMemoryEqpStore(List.of(eqp1, eqp2)),
                assembler,
                properties
        );

        cache.reloadAll();

        final BusinessModelRuntimeSnapshot snapshot = cache.currentSnapshot();
        Assertions.assertEquals(2, snapshot.bindingCount());
        Assertions.assertEquals(2, snapshot.runtimeCount());
        Assertions.assertTrue(cache.findRuntimeByEqpId("EQP-1").orElseThrow().hasWorkflow("S6F11"));
        Assertions.assertTrue(cache.findRuntimeByEqpId("EQP-2").orElseThrow().hasWorkflow("SOCKET_IN"));
    }

    @Test
    void shouldKeepPreviousSnapshotWhenReloadFails() {
        final OffsetDateTime now = OffsetDateTime.now();

        final TcEqp eqp = new TcEqp(1L, "EQP-1", ProtocolType.HSMS, "ACTIVE", 0, "127.0.0.1", 5001, 101L, true, now, now, "SYSTEM", "SYSTEM");
        final TcModel model = new TcModel(101L, 101L, "MODEL-HSMS", "v1", ProtocolType.HSMS, ModelStatus.ACTIVE, "NORI", now, now, "SYSTEM", "SYSTEM");
        final TcModelWorkflow workflow = new TcModelWorkflow(1L, 101L, "WF-HSMS", "S6F11", "E1", "T1", null, "ACT-1", null, now);
        final TcModelMdf mdf = new TcModelMdf(
                1L,
                101L,
                "MDF-101",
                "<mdf><REQ_EQP>CMD=REQ EQPID={eqpId}</REQ_EQP></mdf>".getBytes(StandardCharsets.UTF_8),
                now
        );

        final BusinessModelCacheProperties properties = createValidModelCachePropertiesForTest(1);

        final BusinessModelRuntimeAssembler assembler = new BusinessModelRuntimeAssembler(
                new ModelCacheTestFixtures.InMemoryModelStore(List.of(model)),
                new ModelCacheTestFixtures.InMemoryWorkflowStore(Map.of(101L, List.of(workflow))),
                new ModelCacheTestFixtures.InMemorySecsMessageStore(Map.of()),
                new ModelCacheTestFixtures.InMemorySocketMessageStore(Map.of()),
                new ModelCacheTestFixtures.InMemoryVariableIdStore(Map.of()),
                new ModelCacheTestFixtures.InMemoryMdfStore(List.of(mdf)),
                new BusinessMdfRuntimeParser(),
                properties
        );

        final BusinessModelRuntimeCache cache = new BusinessModelRuntimeCache(
                new ModelCacheTestFixtures.InMemoryEqpStore(List.of(eqp)),
                assembler,
                properties
        );
        cache.reloadAll();
        final BusinessModelRuntimeSnapshot before = cache.currentSnapshot();

        Assertions.assertThrows(IllegalStateException.class, () -> cache.reloadModelRuntime(999L));

        final BusinessModelRuntimeSnapshot after = cache.currentSnapshot();
        Assertions.assertEquals(before.bindingCount(), after.bindingCount());
        Assertions.assertEquals(before.runtimeCount(), after.runtimeCount());
        Assertions.assertTrue(after.findRuntimeByEqpId("EQP-1").isPresent());
    }

    /**
     * 테스트용 캐시 프로퍼티를 생성합니다.
     */
    private static BusinessModelCacheProperties createValidModelCachePropertiesForTest(final int pageSize) {
        final BusinessModelCacheProperties properties = new BusinessModelCacheProperties();
        properties.setLoadOnStartup(false);
        properties.setFailFastOnStartup(false);
        properties.setPageSize(pageSize);
        properties.validate();
        return properties;
    }
}
