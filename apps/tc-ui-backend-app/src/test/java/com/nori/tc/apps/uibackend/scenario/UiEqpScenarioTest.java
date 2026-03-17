package com.nori.tc.apps.uibackend.scenario;

import com.nori.tc.db.domain.common.eqp.ControlState;
import com.nori.tc.db.domain.common.eqp.EqpState;
import com.nori.tc.db.domain.common.eqp.LogLevel;
import com.nori.tc.db.domain.common.model.ModelStatus;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpHsms;
import com.nori.tc.db.domain.eqp.TcEqpLog;
import com.nori.tc.db.domain.eqp.TcEqpParam;
import com.nori.tc.db.domain.eqp.TcEqpParamVersion;
import com.nori.tc.db.domain.eqp.TcEqpState;
import com.nori.tc.db.domain.jar.TcJarBusiness;
import com.nori.tc.db.domain.jar.TcJarGateway;
import com.nori.tc.db.domain.model.TcModel;
import com.nori.tc.ui.core.eqp.EqpManagementCommand;
import com.nori.tc.ui.core.eqp.EqpManagementOptions;
import com.nori.tc.ui.core.eqp.EqpManagementSnapshot;
import com.nori.tc.ui.core.exception.UiConflictException;
import com.nori.tc.ui.core.model.AsyncResultEntry;
import com.nori.tc.ui.core.model.UiCommandReply;
import com.nori.tc.ui.core.registry.DualResponseRegistry;
import com.nori.tc.ui.core.registry.UiDualTaskFinalResult;
import com.nori.tc.ui.domain.task.UiTaskResult;
import com.nori.tc.ui.domain.task.UiTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EQP 관리 시나리오 테스트입니다.
 *
 * <p>T2 기준 CRUD orchestration과 manage/options 조회, 기존 START/END polling 공존을 검증합니다.</p>
 */
@DisplayName("T2 EQP 관리 시나리오")
class UiEqpScenarioTest extends UiBackendScenarioTestSupport {

    @BeforeEach
    void setUpValidToken() {
        lenient().when(tokenCachePort.get(TEST_TOKEN))
                .thenReturn(Optional.of(principalWithPermission(EQP_MANAGE_PERM)));
    }

    @Test
    @DisplayName("EQP 생성은 DB 저장 후 DualResponse 성공 시 200을 반환합니다")
    void EQP_CREATE_관리요청_성공_200() throws Exception {
        when(modelCrudPort.findByModelVersionKey(101L))
                .thenReturn(Optional.of(sampleModel(101L, ProtocolType.SECS, ModelStatus.DEVELOP)));
        when(eqpCrudPort.create(any()))
                .thenReturn(sampleManagementSnapshot("EQP-CREATE-001", ProtocolType.SECS, true, true, false));

        final MvcResult mvcResult = mockMvc.perform(post("/api/eqp")
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJsonWithoutJars("EQP-CREATE-001")))
                .andExpect(request().asyncStarted())
                .andReturn();

        verify(gatewayEventPublishPort, timeout(1000)).publish(any());
        verify(businessEventPublishPort, timeout(1000)).publish(any());

        final String traceId = captureDualTraceId();
        completeDualSuccess(traceId);

        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eqpCrudPort).create(any());
    }

    @Test
    @DisplayName("EQP 관리 상세 조회는 공통/로그/model/param 정보를 함께 반환합니다")
    void EQP_MANAGE_상세조회_200() throws Exception {
        final OffsetDateTime now = OffsetDateTime.parse("2026-03-11T10:15:30+09:00");
        when(eqpManageQueryPort.findManageSnapshotByEqpId(TEST_EQP_ID))
                .thenReturn(Optional.of(sampleManagementSnapshot(
                        TEST_EQP_ID,
                        ProtocolType.SECS,
                        false,
                        false,
                        true,
                        "v1",
                        List.of(
                                new TcEqpParam(11L, 1L, "PARAM_A", "v2", "20", "param latest", "SYSTEM", now),
                                new TcEqpParam(12L, 1L, "PARAM_A", "v1", "10", "param previous", "SYSTEM", now)
                        ),
                        List.of(
                                new TcEqpParamVersion(11L, 1L, "v2", "latest version", now, now, "SYSTEM", "SYSTEM"),
                                new TcEqpParamVersion(12L, 1L, "v1", "previous version", now, now, "SYSTEM", "SYSTEM")
                        )
                )));

        mockMvc.perform(get("/api/eqp/{eqpId}/manage", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eqpId").value(TEST_EQP_ID))
                .andExpect(jsonPath("$.data.commInterface").value("SECS"))
                .andExpect(jsonPath("$.data.logPolicy.logLevel").value("INFO"))
                .andExpect(jsonPath("$.data.jars.gatewayJarFileName").value("gateway-main.jar"))
                .andExpect(jsonPath("$.data.modelBinding.modelName").value("MODEL-SECS-01"))
                .andExpect(jsonPath("$.data.appliedParamVersion").value("v1"))
                .andExpect(jsonPath("$.data.appliedParamDescription").value("previous version"))
                .andExpect(jsonPath("$.data.paramVersions[0].paramVersion").value("v2"))
                .andExpect(jsonPath("$.data.paramVersions[0].description").value("latest version"));
    }

    @Test
    @DisplayName("EQP 관리 상세 조회는 applied_param_version이 없으면 legacy summary 첫 버전으로 fallback하고 EDIT는 제외합니다")
    void EQP_MANAGE_상세조회_legacyFallbackAndExcludeEdit() throws Exception {
        final OffsetDateTime now = OffsetDateTime.parse("2026-03-11T10:15:30+09:00");
        when(eqpManageQueryPort.findManageSnapshotByEqpId(TEST_EQP_ID))
                .thenReturn(Optional.of(sampleManagementSnapshot(
                        TEST_EQP_ID,
                        ProtocolType.SECS,
                        false,
                        false,
                        true,
                        null,
                        List.of(
                                new TcEqpParam(11L, 1L, "PARAM_A", "v3", "30", "latest", "SYSTEM", now),
                                new TcEqpParam(12L, 1L, "PARAM_B", "EDIT", "999", "editing", "tester", now),
                                new TcEqpParam(13L, 1L, "PARAM_A", "v2", "20", "previous", "SYSTEM", now)
                        ),
                        List.of()
                )));

        mockMvc.perform(get("/api/eqp/{eqpId}/manage", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appliedParamVersion").value("v3"))
                .andExpect(jsonPath("$.data.appliedParamDescription").value("latest"))
                .andExpect(jsonPath("$.data.paramVersions.length()").value(2))
                .andExpect(jsonPath("$.data.paramVersions[0].paramVersion").value("v3"))
                .andExpect(jsonPath("$.data.paramVersions[1].paramVersion").value("v2"));
    }

    @Test
    @DisplayName("EQP 관리 옵션 조회는 jar/socket/model 드롭다운 데이터를 반환합니다")
    void EQP_OPTIONS_조회_200() throws Exception {
        when(eqpOptionsQueryPort.loadOptions()).thenReturn(new EqpManagementOptions(
                List.of("JSON", "XML"),
                List.of("gateway-main.jar"),
                List.of("business-main.jar"),
                List.of(new EqpManagementOptions.ModelOption(
                        101L,
                        11L,
                        "MODEL-SECS-01",
                        null,
                        "EDIT",
                        ProtocolType.SECS,
                        ModelStatus.DEVELOP
                )),
                List.of(new EqpManagementOptions.ModelOption(
                        201L,
                        21L,
                        "MODEL-SECS-OPS",
                        null,
                        "EDIT",
                        ProtocolType.SECS,
                        ModelStatus.OPERATE
                ))
        ));

        mockMvc.perform(get("/api/eqp/options")
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.socketProtocolTypes[0]").value("JSON"))
                .andExpect(jsonPath("$.data.gatewayJarFileNames[0]").value("gateway-main.jar"))
                .andExpect(jsonPath("$.data.businessJarFileNames[0]").value("business-main.jar"))
                .andExpect(jsonPath("$.data.developModelOptions[0].modelName").value("MODEL-SECS-01"))
                .andExpect(jsonPath("$.data.operateModelOptions[0].status").value("OPERATE"));
    }

    @Test
    @DisplayName("SECS EQP 수정 요청에 hsmsSettings가 없으면 400이고 runtime 재발행이 없어야 합니다")
    void EQP_UPDATE_입력검증실패_runtime재발행없음() throws Exception {
        when(eqpCrudPort.findSnapshotByEqpId(TEST_EQP_ID))
                .thenReturn(Optional.of(sampleManagementSnapshot(TEST_EQP_ID, ProtocolType.SECS, false, false, true)));

        final MvcResult mvcResult = mockMvc.perform(put("/api/eqp/{eqpId}", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commMode": "ACTIVE",
                                  "isDev": true,
                                  "routePartition": 1,
                                  "eqpIp": "127.0.0.1",
                                  "eqpPort": 5000,
                                  "modelVersionKey": 101,
                                  "gatewayJarFileName": "gateway-main.jar",
                                  "businessJarFileName": "business-main.jar"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verify(eqpCrudPort, never()).update(anyString(), any());
        verify(gatewayEventPublishPort, never()).publish(any());
        verify(businessEventPublishPort, never()).publish(any());
    }

    @Test
    @DisplayName("EQP 수정에서 isDev와 모델 상태가 불일치하면 400이고 DB 저장이 수행되지 않습니다")
    void EQP_UPDATE_isDev_모델상태불일치_400() throws Exception {
        when(eqpCrudPort.findSnapshotByEqpId(TEST_EQP_ID))
                .thenReturn(Optional.of(sampleManagementSnapshot(TEST_EQP_ID, ProtocolType.SECS, false, false, false)));
        when(modelCrudPort.findByModelVersionKey(101L))
                .thenReturn(Optional.of(sampleModel(101L, ProtocolType.SECS, ModelStatus.DEVELOP)));

        final MvcResult mvcResult = mockMvc.perform(put("/api/eqp/{eqpId}", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commMode": "ACTIVE",
                                  "isDev": false,
                                  "routePartition": 1,
                                  "eqpIp": "127.0.0.1",
                                  "eqpPort": 5000,
                                  "modelVersionKey": 101,
                                  "hsmsSettings": {
                                    "deviceId": 0,
                                    "t3Timeout": 45,
                                    "t5Timeout": 10,
                                    "t6Timeout": 5,
                                    "t7Timeout": 10,
                                    "t8Timeout": 5,
                                    "linkTestEnabled": true,
                                    "linkTestInterval": 60,
                                    "maxMsgBytes": 10485760
                                  }
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verify(eqpCrudPort, never()).update(anyString(), any());
        verify(gatewayEventPublishPort, never()).publish(any());
        verify(businessEventPublishPort, never()).publish(any());
    }

    @Test
    @DisplayName("EQP 수정에서 jar가 변경되면 update 후 jar reload까지 수행하고 200을 반환합니다")
    void EQP_UPDATE_jar변경시_reload까지_200() throws Exception {
        when(eqpCrudPort.findSnapshotByEqpId(TEST_EQP_ID))
                .thenReturn(Optional.of(sampleManagementSnapshot(TEST_EQP_ID, ProtocolType.SECS, true, false, true)));
        when(modelCrudPort.findByModelVersionKey(101L))
                .thenReturn(Optional.of(sampleModel(101L, ProtocolType.SECS, ModelStatus.DEVELOP)));
        when(eqpCrudPort.update(anyString(), any()))
                .thenReturn(sampleManagementSnapshotWithJarNames(
                        TEST_EQP_ID,
                        ProtocolType.SECS,
                        true,
                        false,
                        "gateway-next.jar",
                        "business-next.jar"
                ));

        final MvcResult mvcResult = mockMvc.perform(put("/api/eqp/{eqpId}", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commMode": "PASSIVE",
                                  "isDev": true,
                                  "routePartition": 1,
                                  "eqpIp": "127.0.0.1",
                                  "eqpPort": 5000,
                                  "modelVersionKey": 101,
                                  "appliedParamVersion": "v3",
                                  "gatewayJarFileName": "gateway-next.jar",
                                  "businessJarFileName": "business-next.jar",
                                  "hsmsSettings": {
                                    "deviceId": 0,
                                    "t3Timeout": 45,
                                    "t5Timeout": 10,
                                    "t6Timeout": 5,
                                    "t7Timeout": 10,
                                    "t8Timeout": 5,
                                    "linkTestEnabled": true,
                                    "linkTestInterval": 60,
                                    "maxMsgBytes": 10485760
                                  }
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        final String updateTraceId = captureDualTraceId();
        completeDualSuccess(updateTraceId);

        final String jarReloadTraceId = captureDualTraceIds(2).get(1);
        completeDualSuccess(jarReloadTraceId);

        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        final ArgumentCaptor<EqpManagementCommand.Update> updateCaptor = ArgumentCaptor.forClass(EqpManagementCommand.Update.class);
        verify(eqpCrudPort).update(anyString(), updateCaptor.capture());
        assertEquals(TEST_USER_ID, updateCaptor.getValue().actor());
        assertEquals("PASSIVE", updateCaptor.getValue().commMode());
        assertEquals("v3", updateCaptor.getValue().appliedParamVersion());
        verify(gatewayEventPublishPort, times(2)).publish(any());
        verify(businessEventPublishPort, times(2)).publish(any());
    }

    @Test
    @DisplayName("EQP 수정에서 DB 충돌이 발생하면 409와 충돌 메시지를 반환합니다")
    void EQP_UPDATE_db충돌_409() throws Exception {
        when(eqpCrudPort.findSnapshotByEqpId(TEST_EQP_ID))
                .thenReturn(Optional.of(sampleManagementSnapshot(TEST_EQP_ID, ProtocolType.SECS, true, false, true)));
        when(modelCrudPort.findByModelVersionKey(101L))
                .thenReturn(Optional.of(sampleModel(101L, ProtocolType.SECS, ModelStatus.DEVELOP)));
        when(eqpCrudPort.update(anyString(), any()))
                .thenThrow(new UiConflictException("EQP 수정 중 충돌이 발생했습니다."));

        final MvcResult mvcResult = mockMvc.perform(put("/api/eqp/{eqpId}", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commMode": "PASSIVE",
                                  "isDev": true,
                                  "routePartition": 1,
                                  "eqpIp": "127.0.0.1",
                                  "eqpPort": 5000,
                                  "modelVersionKey": 101,
                                  "appliedParamVersion": "v3",
                                  "gatewayJarFileName": "gateway-next.jar",
                                  "businessJarFileName": "business-next.jar",
                                  "hsmsSettings": {
                                    "deviceId": 0,
                                    "t3Timeout": 45,
                                    "t5Timeout": 10,
                                    "t6Timeout": 5,
                                    "t7Timeout": 10,
                                    "t8Timeout": 5,
                                    "linkTestEnabled": true,
                                    "linkTestInterval": 60,
                                    "maxMsgBytes": 10485760
                                  }
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.errorMsg").value("EQP 수정 중 충돌이 발생했습니다."));
    }

    @Test
    @DisplayName("EQP 생성에서 runtime sync가 실패하면 runtime delete와 DB delete로 보상합니다")
    void EQP_CREATE_runtimeSync실패시_보상수행() throws Exception {
        final String eqpId = "EQP-CREATE-ROLLBACK";

        when(modelCrudPort.findByModelVersionKey(101L))
                .thenReturn(Optional.of(sampleModel(101L, ProtocolType.SECS, ModelStatus.DEVELOP)));
        when(eqpCrudPort.create(any()))
                .thenReturn(sampleManagementSnapshot(eqpId, ProtocolType.SECS, true, true, false));

        final MvcResult mvcResult = mockMvc.perform(post("/api/eqp")
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJsonWithoutJars(eqpId)))
                .andExpect(request().asyncStarted())
                .andReturn();

        final String createTraceId = captureDualTraceIds(1).getFirst();
        completeDualFailure(createTraceId, "SYNC_FAILED", "runtime sync failed");

        final String rollbackTraceId = captureDualTraceIds(2).get(1);
        completeDualSuccess(rollbackTraceId);

        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SYNC_FAILED"));

        verify(eqpCrudPort).create(any());
        verify(eqpCrudPort).delete(eqpId);
        verify(gatewayEventPublishPort, times(2)).publish(any());
        verify(businessEventPublishPort, times(2)).publish(any());
    }

    @Test
    @DisplayName("EQP 삭제는 END 성공 후 DB 삭제와 DualResponse 성공 시 200을 반환합니다")
    void EQP_DELETE_END후_삭제_200() throws Exception {
        when(eqpCrudPort.findSnapshotByEqpId(TEST_EQP_ID))
                .thenReturn(Optional.of(sampleManagementSnapshot(TEST_EQP_ID, ProtocolType.SECS, false, false, true)));
        when(asyncResultStorePort.getWithStatus(anyString()))
                .thenAnswer(invocation -> Optional.of(AsyncResultEntry.completed(
                        invocation.getArgument(0),
                        new UiCommandReply(
                                invocation.getArgument(0),
                                DualResponseRegistry.SOURCE_GATEWAY,
                                "EQP_END_REP",
                                TEST_EQP_ID,
                                "SECS",
                                UiTaskStatus.PASS,
                                null,
                                null
                        )
                )));

        final MvcResult mvcResult = mockMvc.perform(delete("/api/eqp/{eqpId}", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andExpect(request().asyncStarted())
                .andReturn();

        final String traceId = captureDualTraceId();
        completeDualSuccess(traceId);

        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eqpCrudPort).delete(TEST_EQP_ID);
        verify(gatewayEventPublishPort, times(2)).publish(any());
        verify(businessEventPublishPort, times(1)).publish(any());
    }

    @Test
    @DisplayName("EQP 삭제에서 END가 타임아웃이면 DB 삭제와 보상 발행을 중단합니다")
    void EQP_DELETE_END_타임아웃이면_삭제중단() throws Exception {
        when(eqpCrudPort.findSnapshotByEqpId(TEST_EQP_ID))
                .thenReturn(Optional.of(sampleManagementSnapshot(TEST_EQP_ID, ProtocolType.SECS, false, false, true)));
        when(asyncResultStorePort.getWithStatus(anyString()))
                .thenAnswer(invocation -> Optional.of(AsyncResultEntry.timeout(invocation.getArgument(0))));

        final MvcResult mvcResult = mockMvc.perform(delete("/api/eqp/{eqpId}", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TIMEOUT"));

        verify(eqpCrudPort, never()).delete(anyString());
        verify(gatewayEventPublishPort, times(1)).publish(any());
        verify(businessEventPublishPort, never()).publish(any());
    }

    @Test
    @DisplayName("EQP_START는 202와 traceId를 즉시 반환합니다")
    void EQP_START_202_즉시반환_traceId() throws Exception {
        mockMvc.perform(post("/api/eqp/{eqpId}/start", TEST_EQP_ID)
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interfaceType":"SECS"}
                                """))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.traceId").isNotEmpty());

        verify(gatewayEventPublishPort).publish(any());
        verify(businessEventPublishPort, never()).publish(any());
    }

    @Test
    @DisplayName("GET /api/async/{traceId}는 완료된 START 결과를 반환합니다")
    void ASYNC_POLLING_결과있음_200() throws Exception {
        final String traceId = "test-trace-id-eqp-start-001";
        final UiCommandReply reply = new UiCommandReply(
                traceId,
                DualResponseRegistry.SOURCE_GATEWAY,
                "EQP_START_REP",
                TEST_EQP_ID,
                "SECS",
                UiTaskStatus.PASS,
                null,
                null
        );
        when(asyncResultStorePort.getWithStatus(traceId))
                .thenReturn(Optional.of(AsyncResultEntry.completed(traceId, reply)));

        mockMvc.perform(get("/api/async/{traceId}", traceId)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.traceId").value(traceId))
                .andExpect(jsonPath("$.data.eqpId").value(TEST_EQP_ID))
                .andExpect(jsonPath("$.data.status").value("PASS"));
    }

    @Test
    @DisplayName("GET /api/async/{traceId}는 PENDING 상태를 202로 반환합니다")
    void ASYNC_POLLING_대기상태_202() throws Exception {
        final String traceId = "pending-trace-id";
        when(asyncResultStorePort.getWithStatus(traceId))
                .thenReturn(Optional.of(AsyncResultEntry.pending(traceId, System.currentTimeMillis() + 30_000L)));

        mockMvc.perform(get("/api/async/{traceId}", traceId)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/async/{traceId}는 TIMEOUT 상태를 408로 반환합니다")
    void ASYNC_POLLING_타임아웃_408() throws Exception {
        final String traceId = "timeout-trace-id";
        when(asyncResultStorePort.getWithStatus(traceId))
                .thenReturn(Optional.of(AsyncResultEntry.timeout(traceId)));

        mockMvc.perform(get("/api/async/{traceId}", traceId)
                        .cookie(authCookie())
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isRequestTimeout())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("TIMEOUT"));
    }

    @Test
    @DisplayName("route_partition 미배정으로 Gateway 발행이 실패하면 START는 500을 반환합니다")
    void U13_route_partition_미배정_발행차단_500() throws Exception {
        doThrow(new IllegalStateException("ROUTE_PARTITION_NOT_ASSIGNED"))
                .when(gatewayEventPublishPort).publish(any());

        mockMvc.perform(post("/api/eqp/{eqpId}/start", "NO-ROUTE-EQP")
                        .cookie(authCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interfaceType":"SECS"}
                                """))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PUBLISH_FAILED"));
    }

    private String captureDualTraceId() {
        final ArgumentCaptor<String> traceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(dualResponseRegistry, timeout(1000)).register(traceIdCaptor.capture(), anyLong());
        return traceIdCaptor.getValue();
    }

    private List<String> captureDualTraceIds(final int expectedCount) {
        final ArgumentCaptor<String> traceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(dualResponseRegistry, timeout(1000).times(expectedCount)).register(traceIdCaptor.capture(), anyLong());
        return traceIdCaptor.getAllValues();
    }

    private void completeDualSuccess(final String traceId) {
        final UiTaskResult gatewayResult = UiTaskResult.pass(traceId, DualResponseRegistry.SOURCE_GATEWAY);
        final UiTaskResult businessResult = UiTaskResult.pass(traceId, DualResponseRegistry.SOURCE_BUSINESS);
        when(dualResponseRedisPort.getResult(traceId))
                .thenReturn(Optional.of(new UiDualTaskFinalResult(
                        traceId,
                        true,
                        gatewayResult,
                        businessResult
                )));
        awaitPendingTraceId(traceId);
        dualResponseRegistry.completeFromRedis(traceId);
    }

    private void completeDualFailure(
            final String traceId,
            final String errorCode,
            final String errorMessage
    ) {
        final UiTaskResult gatewayResult = UiTaskResult.pass(traceId, DualResponseRegistry.SOURCE_GATEWAY);
        final UiTaskResult businessResult = UiTaskResult.fail(
                traceId,
                DualResponseRegistry.SOURCE_BUSINESS,
                errorCode,
                errorMessage
        );
        when(dualResponseRedisPort.getResult(traceId))
                .thenReturn(Optional.of(new UiDualTaskFinalResult(
                        traceId,
                        false,
                        gatewayResult,
                        businessResult
                )));
        awaitPendingTraceId(traceId);
        dualResponseRegistry.completeFromRedis(traceId);
    }

    /**
     * 비동기 worker가 해당 traceId를 registry에 등록할 때까지 잠시 대기합니다.
     *
     * <p>테스트 스레드가 완료 신호를 너무 빨리 주면 registry 등록 전에 신호가 도착해
     * 시나리오 테스트가 간헐적으로 timeout 될 수 있으므로 race를 제거합니다.</p>
     */
    private void awaitPendingTraceId(final String traceId) {
        final long deadlineNanos = System.nanoTime() + 1_000_000_000L;

        while (System.nanoTime() < deadlineNanos) {
            if (dualResponseRegistry.isPending(traceId)) {
                return;
            }

            try {
                Thread.sleep(10L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("DualResponse traceId 대기 중 인터럽트가 발생했습니다. traceId=" + traceId, interruptedException);
            }
        }

        throw new IllegalStateException("DualResponse traceId가 registry에 등록되지 않았습니다. traceId=" + traceId);
    }

    private String createRequestJsonWithoutJars(final String eqpId) {
        return """
                {
                  "eqpId": "%s",
                  "interfaceType": "SECS",
                  "commMode": "ACTIVE",
                  "isDev": true,
                  "routePartition": 1,
                  "eqpIp": "127.0.0.1",
                  "eqpPort": 5000,
                  "modelVersionKey": 101,
                  "logSettings": {
                    "logLevel": "INFO",
                    "logRetentionDays": 7,
                    "logPath": "/var/log/eqp"
                  },
                  "hsmsSettings": {
                    "deviceId": 0,
                    "t3Timeout": 45,
                    "t5Timeout": 10,
                    "t6Timeout": 5,
                    "t7Timeout": 10,
                    "t8Timeout": 5,
                    "linkTestEnabled": true,
                    "linkTestInterval": 60,
                    "maxMsgBytes": 10485760
                  }
                }
                """.formatted(eqpId);
    }

    private TcModel sampleModel(
            final long modelVersionKey,
            final ProtocolType protocolType,
            final ModelStatus status
    ) {
        final OffsetDateTime now = OffsetDateTime.parse("2026-03-11T10:15:30+09:00");
        return new TcModel(
                modelVersionKey,
                modelVersionKey + 100,
                "MODEL-SECS-01",
                null,
                "EDIT",
                protocolType,
                status,
                "test model",
                "NORI",
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }

    private EqpManagementSnapshot sampleManagementSnapshot(
            final String eqpId,
            final ProtocolType protocolType,
            final boolean isDev,
            final boolean alreadyStopped,
            final boolean includeJars
    ) {
        return sampleManagementSnapshot(
                eqpId,
                protocolType,
                isDev,
                alreadyStopped,
                includeJars ? "gateway-main.jar" : null,
                includeJars ? "business-main.jar" : null,
                "v2",
                null,
                null
        );
    }

    private EqpManagementSnapshot sampleManagementSnapshot(
            final String eqpId,
            final ProtocolType protocolType,
            final boolean isDev,
            final boolean alreadyStopped,
            final boolean includeJars,
            final String appliedParamVersion,
            final List<TcEqpParam> params,
            final List<TcEqpParamVersion> paramVersionMetas
    ) {
        return sampleManagementSnapshot(
                eqpId,
                protocolType,
                isDev,
                alreadyStopped,
                includeJars ? "gateway-main.jar" : null,
                includeJars ? "business-main.jar" : null,
                appliedParamVersion,
                params,
                paramVersionMetas
        );
    }

    private EqpManagementSnapshot sampleManagementSnapshotWithJarNames(
            final String eqpId,
            final ProtocolType protocolType,
            final boolean isDev,
            final boolean alreadyStopped,
            final String gatewayJarFileName,
            final String businessJarFileName
    ) {
        return sampleManagementSnapshot(
                eqpId,
                protocolType,
                isDev,
                alreadyStopped,
                gatewayJarFileName,
                businessJarFileName,
                "v2",
                null,
                null
        );
    }

    private EqpManagementSnapshot sampleManagementSnapshot(
            final String eqpId,
            final ProtocolType protocolType,
            final boolean isDev,
            final boolean alreadyStopped,
            final String gatewayJarFileName,
            final String businessJarFileName,
            final String appliedParamVersion,
            final List<TcEqpParam> params,
            final List<TcEqpParamVersion> paramVersionMetas
    ) {
        final OffsetDateTime now = OffsetDateTime.parse("2026-03-11T10:15:30+09:00");
        final long modelVersionKey = isDev ? 101L : 201L;
        final List<TcEqpParam> resolvedParams = params == null
                ? List.of(
                new TcEqpParam(11L, 1L, "PARAM_A", "v2", "20", "latest", "SYSTEM", now),
                new TcEqpParam(12L, 1L, "PARAM_B", "v2", "30", "latest", "SYSTEM", now),
                new TcEqpParam(13L, 1L, "PARAM_A", "v1", "10", "previous", "SYSTEM", now)
        )
                : params;
        final List<TcEqpParamVersion> resolvedParamVersionMetas = paramVersionMetas == null
                ? List.of(
                new TcEqpParamVersion(11L, 1L, "v2", "latest", now, now, "SYSTEM", "SYSTEM"),
                new TcEqpParamVersion(12L, 1L, "v1", "previous", now, now, "SYSTEM", "SYSTEM")
        )
                : paramVersionMetas;

        return new EqpManagementSnapshot(
                new TcEqp(
                        1L,
                        eqpId,
                        protocolType,
                        "ACTIVE",
                        isDev,
                        1,
                        "127.0.0.1",
                        5000,
                        modelVersionKey,
                        appliedParamVersion,
                        true,
                        now,
                        now,
                        "SYSTEM",
                        "SYSTEM"
                ),
                sampleModel(modelVersionKey, protocolType, isDev ? ModelStatus.DEVELOP : ModelStatus.OPERATE),
                protocolType == ProtocolType.SECS ? new TcEqpHsms(
                        1L,
                        0,
                        45,
                        10,
                        5,
                        10,
                        5,
                        true,
                        60,
                        10_485_760L,
                        now,
                        now
                ) : null,
                null,
                new TcEqpLog(
                        1L,
                        LogLevel.INFO,
                        7,
                        "/var/log/eqp",
                        now
                ),
                new TcEqpState(
                        1L,
                        alreadyStopped ? ControlState.DOWN : ControlState.REMOTE,
                        alreadyStopped ? EqpState.DOWN : EqpState.RUN,
                        now,
                        "TEST",
                        "test state",
                        now
                ),
                alreadyStopped ? "DISCONNECTED" : "CONNECTED",
                List.of(),
                resolvedParams,
                resolvedParamVersionMetas,
                gatewayJarFileName != null
                        ? new TcJarGateway(
                        1L,
                        gatewayJarFileName,
                        new byte[]{1},
                        now,
                        now,
                        "SYSTEM",
                        "SYSTEM"
                )
                        : null,
                businessJarFileName != null
                        ? new TcJarBusiness(
                        1L,
                        businessJarFileName,
                        new byte[]{2},
                        now,
                        now,
                        "SYSTEM",
                        "SYSTEM"
                )
                        : null
        );
    }
}
