package com.nori.tc.apps.uibackend.scenario;

import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyMessage;
import com.nori.tc.ui.core.registry.DualResponseRegistry;
import com.nori.tc.ui.core.registry.UiDualTaskFinalResult;
import com.nori.tc.ui.domain.task.UiTaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 시나리오 검증: 설비(Equipment) 관리 API 흐름 테스트입니다.
 *
 * <p>검증 시나리오:</p>
 * <ol start="6">
 *   <li>POST /api/eqp (EQP_CREATE) → Gateway + Business 동시 발행 → DualResponse 수신 → 200</li>
 *   <li>POST /api/eqp/{id}/start (EQP_START) → 202 즉시 반환 + traceId</li>
 *   <li>GET /api/async/{traceId} → Gateway reply 조회 → 200 + PASS 결과 (polling 검증)</li>
 *   <li>route_partition 미배정 eqpId → Gateway 발행 차단 → 500 PUBLISH_FAILED (U13)</li>
 * </ol>
 *
 * <p>DualResponse 테스트 전략 (시나리오 6):</p>
 * <p>{@link DualResponseRegistry}는 {@code @MockitoSpyBean}으로 실제 동작을 유지합니다.
 * {@link ArgumentCaptor}로 EqpController 내부에서 생성된 traceId를 캡처하고,
 * Redis 조회 결과를 주입하고 {@link DualResponseRegistry#completeFromRedis(String)}를 호출하여
 * Gateway/Business 완료 신호를 시뮬레이션합니다.</p>
 *
 * <p>DeferredResult 비동기 패턴 (MockMvc):</p>
 * <ol>
 *   <li>{@code mockMvc.perform(...).andExpect(request().asyncStarted()).andReturn()}
 *       — 비동기 요청 시작, MvcResult 획득</li>
 *   <li>ArgumentCaptor로 traceId 캡처 → {@code completeFromRedis()} 호출로 DeferredResult 완료</li>
 *   <li>{@code mockMvc.perform(asyncDispatch(mvcResult))} — 완료된 DeferredResult 응답 검증</li>
 * </ol>
 */
@DisplayName("Phase 10 시나리오 6~7, 7-a, 10: EQP 설비 관리 흐름 검증")
class UiEqpScenarioTest extends UiBackendScenarioTestSupport {

    private static final Logger log = LoggerFactory.getLogger(UiEqpScenarioTest.class);

    /**
     * EQP 시나리오 공통 사전 설정입니다.
     *
     * <p>EQP 관련 API는 인증된 사용자 컨텍스트에서 호출됩니다.
     * 현재 정책은 closed by default 이므로 EQP_MANAGE 권한을 가진 사용자로 테스트를 수행합니다.</p>
     */
    @BeforeEach
    void setUpValidToken() {
        // 유효한 Bearer 토큰으로 모든 EQP 요청에 인증이 통과하도록 설정합니다.
        // lenient()를 사용하여 특정 테스트에서 토큰을 사용하지 않더라도 불필요한 Stubbing 경고를 방지합니다.
        lenient().when(tokenCachePort.get(TEST_TOKEN))
                .thenReturn(Optional.of(principalWithPermission(EQP_MANAGE_PERM)));
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 6: POST /api/eqp (EQP_CREATE) → DualResponse → 200
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 6: POST /api/eqp (EQP_CREATE) — Gateway + Business 양방향 발행 후 DualResponse 수신 시 200을 반환합니다.
     *
     * <p>처리 흐름:</p>
     * <ol>
     *   <li>POST /api/eqp 요청 → EqpController.create()가 DeferredResult 반환 (비동기 시작)</li>
     *   <li>EqpController는 Kafka 발행 이전에 DualResponseRegistry.register(traceId)를 먼저 호출하여
     *       응답 유실 엣지 케이스를 방어합니다</li>
     *   <li>Gateway 토픽({@code tc.ui.events.gateway}) + Business 토픽({@code tc.ui.events.business}) 동시 발행</li>
     *   <li>ArgumentCaptor로 내부 생성된 traceId를 캡처</li>
     *   <li>DualResponse Redis 조회 결과를 주입</li>
     *   <li>{@link DualResponseRegistry#completeFromRedis(String)} 호출로 CompletableFuture 완료</li>
     *   <li>asyncDispatch → 200 OK 검증</li>
     * </ol>
     */
    @Test
    @DisplayName("시나리오 6: POST /api/eqp → Gateway+Business DualResponse 수신 → 200")
    void EQP_CREATE_DualResponse_성공_200() throws Exception {
        log.info("[시나리오 6] POST /api/eqp → DualResponse → 200 검증 시작");

        // when: POST /api/eqp 비동기 요청 시작 — DeferredResult 반환, Spring async 처리 중
        // gatewayEventPublishPort.publish()와 businessEventPublishPort.publish()는 @MockitoBean no-op으로 동작합니다.
        final MvcResult mvcResult = mockMvc.perform(post("/api/eqp")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eqpId":"EQP-CREATE-001","interfaceType":"HSMS"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        // then-1: Kafka 발행 확인 — Gateway와 Business 토픽 양쪽에 발행되어야 합니다.
        // EqpController.submitDualRequest()는 두 포트를 순서대로 호출하므로 각각 1회씩 검증합니다.
        verify(gatewayEventPublishPort).publish(any());
        verify(businessEventPublishPort).publish(any());

        // then-2: traceId 캡처 — EqpController가 Kafka 발행 직전에 register()를 먼저 호출하므로
        // async 시작 시점에 이미 register() 인자가 기록됩니다.
        final ArgumentCaptor<String> traceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(dualResponseRegistry).register(traceIdCaptor.capture(), anyLong());
        final String capturedTraceId = traceIdCaptor.getValue();

        log.debug("[시나리오 6] 캡처된 traceId={}", capturedTraceId);

        // then-3: Redis 조회 결과 + 완료 신호 시뮬레이션
        final UiTaskResult gatewayResult =
                UiTaskResult.pass(capturedTraceId, DualResponseRegistry.SOURCE_GATEWAY);
        final UiTaskResult businessResult =
                UiTaskResult.pass(capturedTraceId, DualResponseRegistry.SOURCE_BUSINESS);
        when(dualResponseRedisPort.getResult(capturedTraceId))
                .thenReturn(Optional.of(new UiDualTaskFinalResult(
                        capturedTraceId,
                        true,
                        gatewayResult,
                        businessResult
                )));
        dualResponseRegistry.completeFromRedis(capturedTraceId);

        // then-4: DeferredResult 완료 후 비동기 응답 검증
        mockMvc.perform(asyncDispatch(mvcResult))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        log.info("[시나리오 6] EQP_CREATE DualResponse 200 확인 완료. traceId={}", capturedTraceId);
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 7: EQP_START → 202 즉시 반환 + traceId
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 7: POST /api/eqp/{eqpId}/start (EQP_START) — 202 Accepted와 traceId를 즉시 반환합니다.
     *
     * <p>EQP_START는 Gateway 단독 처리이므로 Business 토픽에는 발행하지 않습니다.
     * DeferredResult 없이 ResponseEntity를 즉시 반환하는 동기 흐름입니다.
     * 클라이언트는 반환된 traceId로 {@code GET /api/async/{traceId}}를 polling하여 결과를 확인합니다.</p>
     *
     * <p>검증 포인트:</p>
     * <ul>
     *   <li>HTTP 202 Accepted</li>
     *   <li>data.traceId 비어있지 않음 (UUID 형식)</li>
     *   <li>gatewayEventPublishPort.publish() 1회 호출됨</li>
     *   <li>businessEventPublishPort.publish() 호출 안 됨 (Gateway 단독 처리 설계)</li>
     * </ul>
     */
    @Test
    @DisplayName("시나리오 7: POST /api/eqp/{id}/start → 202 즉시 반환 + traceId")
    void EQP_START_202_즉시반환_traceId() throws Exception {
        log.info("[시나리오 7] POST /api/eqp/{}/start → 202 + traceId 검증 시작", TEST_EQP_ID);

        // when + then: EQP_START 요청 → 202 즉시 반환
        // EqpController.publishLifecycleAndAccept()는 DeferredResult 없이 즉시 응답합니다.
        mockMvc.perform(post("/api/eqp/{eqpId}/start", TEST_EQP_ID)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interfaceType":"HSMS"}
                                """))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                // traceId는 EqpController.generateTraceId()가 UUID v4로 생성합니다.
                .andExpect(jsonPath("$.data.traceId").isNotEmpty());

        // EQP_START는 Gateway 단독 처리이므로:
        // - gatewayEventPublishPort는 반드시 1회 호출되어야 합니다.
        // - businessEventPublishPort는 호출되어서는 안 됩니다 (Business Core 미관여).
        verify(gatewayEventPublishPort).publish(any());
        verify(businessEventPublishPort, never()).publish(any());

        log.info("[시나리오 7] 202 즉시 반환 + traceId 확인 완료");
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 7-a: GET /api/async/{traceId} → Gateway reply polling → 200
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 7-a: GET /api/async/{traceId} — Gateway reply가 Redis에 저장된 경우 200 + 결과를 반환합니다.
     *
     * <p>EQP_START 흐름 중 polling 단계 검증입니다:</p>
     * <ol>
     *   <li>Gateway가 tc.ui.commands 토픽에 EQP_START_REP 발행</li>
     *   <li>UiCommandIngressService 수신 → AsyncResultStorePort.save(traceId, reply) 저장</li>
     *   <li>클라이언트가 GET /api/async/{traceId} 호출 → 200 + PASS 결과 반환</li>
     * </ol>
     *
     * <p>검증 포인트:</p>
     * <ul>
     *   <li>HTTP 200 OK</li>
     *   <li>data.status = "PASS"</li>
     *   <li>data.eqpId = TEST_EQP_ID</li>
     *   <li>결과 없을 때 (처리 중) → 404 Not Found</li>
     * </ul>
     */
    @Test
    @DisplayName("시나리오 7-a: GET /api/async/{traceId} → Gateway reply 조회 → 200 + PASS")
    void ASYNC_POLLING_결과있음_200() throws Exception {
        log.info("[시나리오 7-a] GET /api/async/{{traceId}} → polling → 200 + PASS 검증 시작");

        // given: Gateway가 EQP_START 처리 완료 후 tc.ui.commands에 EQP_START_REP를 발행하고
        // UiCommandIngressService가 수신하여 Redis에 저장한 상태를 시뮬레이션합니다.
        final String traceId = "test-trace-id-eqp-start-001";
        final KafkaUiTaskReplyMessage reply = new KafkaUiTaskReplyMessage(
                new KafkaUiTaskMessage.KafkaUiTaskMetadata(
                        "EQP_START_REP",
                        OffsetDateTime.now().toString(),
                        DualResponseRegistry.SOURCE_GATEWAY,
                        traceId
                ),
                new KafkaUiTaskReplyMessage.KafkaUiTaskReplyData(
                        TEST_EQP_ID,
                        "HSMS",
                        "PASS",
                        null,   // errorMsg: 성공 시 null
                        null    // errorCode: 성공 시 null
                )
        );
        when(asyncResultStorePort.get(traceId)).thenReturn(Optional.of(reply));

        // when + then: polling → 200 + PASS 결과
        mockMvc.perform(get("/api/async/{traceId}", traceId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.traceId").value(traceId))
                .andExpect(jsonPath("$.data.eqpId").value(TEST_EQP_ID))
                .andExpect(jsonPath("$.data.status").value("PASS"));

        log.info("[시나리오 7-a] polling 200 + PASS 확인 완료. traceId={}", traceId);
    }

    /**
     * 시나리오 7-a (대기 중): GET /api/async/{traceId} — Gateway reply가 아직 없을 때 404를 반환합니다.
     *
     * <p>Gateway 처리가 완료되기 전이거나 TTL이 만료된 경우 404 Not Found를 반환합니다.
     * 클라이언트는 일정 간격으로 재시도하여 결과를 확인합니다.</p>
     */
    @Test
    @DisplayName("시나리오 7-a (대기): GET /api/async/{traceId} → 결과 미존재 → 404")
    void ASYNC_POLLING_결과없음_404() throws Exception {
        log.info("[시나리오 7-a 대기] GET /api/async/{{traceId}} → 결과 미존재 → 404 검증 시작");

        // given: 아직 처리 중 (Gateway reply가 Redis에 저장되지 않은 상태)
        when(asyncResultStorePort.get(anyString())).thenReturn(Optional.empty());

        // when + then: 결과 없음 → 404 (처리 중 또는 만료)
        mockMvc.perform(get("/api/async/{traceId}", "pending-trace-id")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isNotFound());

        log.info("[시나리오 7-a 대기] 404 확인 완료");
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 10 (U13): route_partition 미배정 → 발행 차단 + 500
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 10 (U13): route_partition이 배정되지 않은 eqpId로 EQP_START 발행 시도 시 500을 반환합니다.
     *
     * <p>U13 규칙: {@code tc_eqp.route_partition}이 미배정인 eqpId는 Gateway 발행을 차단합니다.
     * 잘못된 파티션으로 발행하면 해당 설비를 담당하는 Gateway 인스턴스가 메시지를 수신하지 못하므로
     * 발행 자체를 거부하고 ERROR 로그를 남겨 운영자가 인지할 수 있도록 합니다.</p>
     *
     * <p>@WebMvcTest 환경에서의 U13 시뮬레이션:</p>
     * <p>{@code @WebMvcTest}에서 {@link com.nori.tc.ui.core.port.messaging.UiGatewayEventPublishPort}는
     * {@code @MockitoBean}으로 교체되어 있어 {@code UiGatewayEventKafkaPublisher}의 실제 U13 검증
     * (route_partition 조회 → empty → ERROR 로그 → IllegalStateException)이 직접 수행되지 않습니다.
     * 따라서 {@code doThrow(IllegalStateException)}으로 U13 차단 동작을 시뮬레이션합니다.</p>
     *
     * <p>EqpController.publishLifecycleAndAccept()는 발행 실패 예외를 catch하여
     * ERROR 로그를 남기고 500 PUBLISH_FAILED를 반환합니다.</p>
     *
     * <p>검증 포인트:</p>
     * <ul>
     *   <li>HTTP 500 Internal Server Error</li>
     *   <li>success=false</li>
     *   <li>errorCode=PUBLISH_FAILED</li>
     * </ul>
     */
    @Test
    @DisplayName("시나리오 10 (U13): route_partition 미배정 eqpId → Gateway 발행 차단 → 500 PUBLISH_FAILED")
    void U13_route_partition_미배정_발행차단_500() throws Exception {
        log.info("[시나리오 10] route_partition 미배정 eqpId → 발행 차단 검증 시작");

        // given: UiGatewayEventKafkaPublisher의 U13 차단 동작을 시뮬레이션합니다.
        // 실제 구현에서 routePartitionLookupPort.findRoutePartition(eqpId)가 빈 Optional을 반환하면
        // UiGatewayEventKafkaPublisher.publish()가 ERROR 로그를 남기고 IllegalStateException을 던집니다.
        doThrow(new IllegalStateException(
                "Gateway 발행 실패: eqpId=NO-ROUTE-EQP의 route_partition이 배정되지 않았습니다. "
                        + "reason=ROUTE_PARTITION_NOT_ASSIGNED"
        )).when(gatewayEventPublishPort).publish(any());

        // when + then: route_partition 미배정 설비로 EQP_START 시도 → 발행 차단 → 500
        // EqpController.publishLifecycleAndAccept()의 catch 블록에서 ERROR 로그 후 500 PUBLISH_FAILED 반환
        mockMvc.perform(post("/api/eqp/{eqpId}/start", "NO-ROUTE-EQP")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"interfaceType":"HSMS"}
                                """))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PUBLISH_FAILED"));

        log.info("[시나리오 10] 발행 차단 → 500 PUBLISH_FAILED 확인 완료");
    }
}
