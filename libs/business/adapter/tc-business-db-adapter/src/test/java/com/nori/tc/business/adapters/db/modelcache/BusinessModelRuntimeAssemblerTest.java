package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.common.model.VariableIdType;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.db.domain.model.TcModelSecsMessage;
import com.nori.tc.db.domain.model.TcModelSocketMessage;
import com.nori.tc.db.domain.model.TcModelVariableId;
import com.nori.tc.db.domain.model.TcModelWorkflow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@link BusinessModelRuntimeAssembler} 단위 테스트입니다.
 */
class BusinessModelRuntimeAssemblerTest {

    @Test
    void shouldAssembleWorkflowIndexesAndMetadata() {
        final long modelKey = 101L;
        final OffsetDateTime now = OffsetDateTime.now();

        final TcModel model = new TcModel(
                modelKey,
                "MODEL-A",
                "v1",
                ProtocolType.HSMS,
                ModelStatus.ACTIVE,
                "NORI",
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );

        final List<TcModelWorkflow> workflows = List.of(
                new TcModelWorkflow(1L, modelKey, "WF-1", "S6F11", "E1", "T1", "{\"op\":\"eq\"}", "ACT-1", "IDX-1", now),
                new TcModelWorkflow(2L, modelKey, "WF-2", "S6F11", "E1", "T2", null, "ACT-2", null, now),
                new TcModelWorkflow(3L, modelKey, "WF-3", "MES_START", null, null, null, "ACT-3", null, now)
        );

        final List<TcModelSecsMessage> secsMessages = List.of(
                new TcModelSecsMessage(1L, modelKey, "S6F11", "Alarm report", "IDX-SECS", now)
        );
        final List<TcModelSocketMessage> socketMessages = List.of(
                new TcModelSocketMessage(1L, modelKey, "SOCKET_HEARTBEAT", "Heartbeat", "IDX-SOCKET", now)
        );
        final List<TcModelVariableId> variableIds = List.of(
                new TcModelVariableId(1L, modelKey, "SV_TEMP", VariableIdType.SVID, "Temperature", now)
        );

        final BusinessModelCacheProperties properties = new BusinessModelCacheProperties();
        properties.setPageSize(2);
        properties.validate();

        final BusinessModelRuntimeAssembler assembler = new BusinessModelRuntimeAssembler(
                new ModelCacheTestFixtures.InMemoryModelStore(List.of(model)),
                new ModelCacheTestFixtures.InMemoryWorkflowStore(Map.of(modelKey, workflows)),
                new ModelCacheTestFixtures.InMemorySecsMessageStore(Map.of(modelKey, secsMessages)),
                new ModelCacheTestFixtures.InMemorySocketMessageStore(Map.of(modelKey, socketMessages)),
                new ModelCacheTestFixtures.InMemoryVariableIdStore(Map.of(modelKey, variableIds)),
                properties
        );

        final TcModelRuntime runtime = assembler.assemble(modelKey);

        Assertions.assertEquals(modelKey, runtime.modelKey());
        Assertions.assertTrue(runtime.hasWorkflow("S6F11"));
        Assertions.assertTrue(runtime.hasWorkflow("MES_START"));
        Assertions.assertEquals(2, runtime.findWorkflowsByMessageName("S6F11").size());
        Assertions.assertEquals(1, runtime.findSecsWorkflows("S6F11", "E1", "T1").size());
        Assertions.assertEquals(1, runtime.secsMessagesByName().size());
        Assertions.assertEquals(1, runtime.socketMessagesByName().size());
        Assertions.assertTrue(runtime.findVariable(VariableIdType.SVID, "SV_TEMP").isPresent());
    }
}

