package com.nori.tc.apps.uibackend.scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 시나리오 검증: DLQ(Dead Letter Queue) 관리 API 흐름 테스트입니다.
 *
 * <p>검증 시나리오:</p>
 * <ol start="8">
 *   <li>GET /api/dlq/gateway → Gateway DLQ 항목 목록 반환</li>
 *   <li>GET /api/dlq/gateway (빈 목록) → 빈 배열 반환</li>
 *   <li>GET /api/dlq/business → Business DLQ 항목 목록 반환</li>
 *   <li>GET /api/dlq/business (빈 목록) → 빈 배열 반환</li>
 * </ol>
 *
 * <p>DLQ 역할:</p>
 * <p>Kafka 메시지 처리 실패 시 Gateway/Business Core가 각각의 Redis에 DLQ 항목을 기록합니다.
 * 운영자는 이 API로 DLQ 현황을 파악하고 문제 해결 후 항목을 수동으로 제거합니다.</p>
 *
 * <p>포트 분리:</p>
 * <ul>
 *   <li>{@link com.nori.tc.ui.core.port.redis.GatewayDlqPort}: Gateway Redis(6379) DLQ 조회/삭제</li>
 *   <li>{@link com.nori.tc.ui.core.port.redis.BusinessDlqPort}: Business Redis(6380) DLQ 조회/삭제</li>
 * </ul>
 */
@DisplayName("Phase 10 시나리오 8~9: DLQ 관리 API 흐름 검증")
class UiDlqScenarioTest extends UiBackendScenarioTestSupport {

    private static final Logger log = LoggerFactory.getLogger(UiDlqScenarioTest.class);

    /**
     * DLQ 시나리오 공통 사전 설정입니다.
     *
     * <p>DLQ 조회 API는 인증된 사용자 컨텍스트에서 호출됩니다.
     * closed by default 정책이므로 DLQ 접근 권한(EQP_MANAGE)을 가진 사용자로 테스트합니다.</p>
     */
    @BeforeEach
    void setUpValidToken() {
        // 유효한 Bearer 토큰으로 모든 DLQ 요청에 인증이 통과하도록 설정합니다.
        // lenient()를 사용하여 특정 테스트에서 토큰을 사용하지 않더라도 불필요한 Stubbing 경고를 방지합니다.
        lenient().when(tokenCachePort.get(TEST_TOKEN))
                .thenReturn(Optional.of(principalWithPermission(EQP_MANAGE_PERM)));
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 8: GET /api/dlq/gateway → Gateway DLQ 목록
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 8: GET /api/dlq/gateway — Gateway Redis에 저장된 DLQ 항목 목록을 반환합니다.
     *
     * <p>DlqController는 GatewayDlqPort.listAllDlq()를 호출하여 결과를 그대로 반환합니다.
     * DLQ 항목은 역직렬화된 임의 타입의 객체이며, 테스트에서는 문자열 목록으로 대체하여 응답 구조를 검증합니다.</p>
     *
     * <p>검증 포인트:</p>
     * <ul>
     *   <li>HTTP 200 OK</li>
     *   <li>success=true</li>
     *   <li>data는 배열 타입</li>
     *   <li>반환된 항목이 GatewayDlqPort.listAllDlq() 결과와 일치</li>
     * </ul>
     */
    @Test
    @DisplayName("시나리오 8: GET /api/dlq/gateway → Gateway DLQ 항목 목록 반환")
    void Gateway_DLQ_목록_조회_200() throws Exception {
        log.info("[시나리오 8] GET /api/dlq/gateway → DLQ 목록 반환 검증 시작");

        // given: Gateway Redis에 DLQ 항목 2건이 저장되어 있는 상태를 시뮬레이션합니다.
        // 실제 운영에서는 Kafka 처리 실패 항목이 직렬화된 객체로 저장됩니다.
        // doReturn().when() 사용: listAllDlq()가 List<?>를 반환하므로
        // when().thenReturn()은 와일드카드 캡처 불일치 컴파일 오류가 발생합니다.
        doReturn(List.of("gateway-dlq-entry-1", "gateway-dlq-entry-2"))
                .when(gatewayDlqPort).listAllDlq();

        // when + then: GET /api/dlq/gateway → 200 + DLQ 항목 2건
        mockMvc.perform(get("/api/dlq/gateway")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("gateway-dlq-entry-1"))
                .andExpect(jsonPath("$.data[1]").value("gateway-dlq-entry-2"));

        log.info("[시나리오 8] Gateway DLQ 목록 조회 확인 완료. count=2");
    }

    /**
     * 시나리오 8-a: GET /api/dlq/gateway — DLQ 항목이 없을 때 빈 배열을 반환합니다.
     *
     * <p>정상 운영 상태에서는 DLQ 항목이 없어야 합니다.
     * 빈 목록일 때 200 OK + 빈 배열이 올바르게 반환되는지 검증합니다.</p>
     */
    @Test
    @DisplayName("시나리오 8-a: GET /api/dlq/gateway (DLQ 없음) → 빈 배열 반환")
    void Gateway_DLQ_빈목록_200() throws Exception {
        log.info("[시나리오 8-a] GET /api/dlq/gateway (빈 목록) 검증 시작");

        // given: DLQ 항목이 없는 정상 운영 상태
        doReturn(List.of()).when(gatewayDlqPort).listAllDlq();

        // when + then: 빈 DLQ → 200 + 빈 배열
        mockMvc.perform(get("/api/dlq/gateway")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        log.info("[시나리오 8-a] Gateway DLQ 빈 배열 반환 확인 완료");
    }

    /**
     * 시나리오 8-b: DELETE /api/dlq/gateway/{dlqId} — DLQ 항목 삭제 성공 시 true를 반환합니다.
     *
     * <p>운영자가 문제를 해결한 후 DLQ 항목을 수동으로 제거하는 흐름입니다.
     * 삭제 성공 시 data=true, 이미 삭제된 경우 data=false로 멱등성을 보장합니다.</p>
     */
    @Test
    @DisplayName("시나리오 8-b: DELETE /api/dlq/gateway/{dlqId} → 삭제 성공 → true 반환")
    void Gateway_DLQ_삭제_성공_200() throws Exception {
        log.info("[시나리오 8-b] DELETE /api/dlq/gateway/{{dlqId}} → 삭제 성공 검증 시작");

        final String dlqId = "gateway-dlq-key-001";

        // given: 해당 DLQ 항목 삭제 성공 시뮬레이션
        when(gatewayDlqPort.deleteDlq(dlqId)).thenReturn(true);

        // when + then: 삭제 요청 → 200 + data=true
        mockMvc.perform(delete("/api/dlq/gateway/{dlqId}", dlqId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));

        log.info("[시나리오 8-b] Gateway DLQ 삭제 성공(true) 확인 완료. dlqId={}", dlqId);
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 9: GET /api/dlq/business → Business DLQ 목록
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 9: GET /api/dlq/business — Business Redis에 저장된 DLQ 항목 목록을 반환합니다.
     *
     * <p>DlqController는 BusinessDlqPort.listAllDlq()를 호출하여 결과를 그대로 반환합니다.
     * Gateway DLQ와 독립적인 Business Redis(6380) 연결을 통해 조회합니다.</p>
     *
     * <p>검증 포인트:</p>
     * <ul>
     *   <li>HTTP 200 OK</li>
     *   <li>success=true</li>
     *   <li>data는 배열 타입</li>
     *   <li>반환된 항목이 BusinessDlqPort.listAllDlq() 결과와 일치</li>
     * </ul>
     */
    @Test
    @DisplayName("시나리오 9: GET /api/dlq/business → Business DLQ 항목 목록 반환")
    void Business_DLQ_목록_조회_200() throws Exception {
        log.info("[시나리오 9] GET /api/dlq/business → DLQ 목록 반환 검증 시작");

        // given: Business Redis에 DLQ 항목 3건이 저장되어 있는 상태를 시뮬레이션합니다.
        doReturn(List.of("biz-dlq-entry-1", "biz-dlq-entry-2", "biz-dlq-entry-3"))
                .when(businessDlqPort).listAllDlq();

        // when + then: GET /api/dlq/business → 200 + DLQ 항목 3건
        mockMvc.perform(get("/api/dlq/business")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("biz-dlq-entry-1"))
                .andExpect(jsonPath("$.data[1]").value("biz-dlq-entry-2"))
                .andExpect(jsonPath("$.data[2]").value("biz-dlq-entry-3"));

        log.info("[시나리오 9] Business DLQ 목록 조회 확인 완료. count=3");
    }

    /**
     * 시나리오 9-a: GET /api/dlq/business — DLQ 항목이 없을 때 빈 배열을 반환합니다.
     *
     * <p>정상 운영 상태에서는 DLQ 항목이 없어야 합니다.
     * 빈 목록일 때 200 OK + 빈 배열이 올바르게 반환되는지 검증합니다.</p>
     */
    @Test
    @DisplayName("시나리오 9-a: GET /api/dlq/business (DLQ 없음) → 빈 배열 반환")
    void Business_DLQ_빈목록_200() throws Exception {
        log.info("[시나리오 9-a] GET /api/dlq/business (빈 목록) 검증 시작");

        // given: DLQ 항목이 없는 정상 운영 상태
        doReturn(List.of()).when(businessDlqPort).listAllDlq();

        // when + then: 빈 DLQ → 200 + 빈 배열
        mockMvc.perform(get("/api/dlq/business")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        log.info("[시나리오 9-a] Business DLQ 빈 배열 반환 확인 완료");
    }

    /**
     * 시나리오 9-b: DELETE /api/dlq/business/{dlqId} — DLQ 항목이 이미 없을 때 false를 반환합니다.
     *
     * <p>DlqController는 멱등성을 보장합니다. 이미 삭제된 항목을 다시 삭제 시도해도
     * 예외 없이 200 OK + data=false를 반환합니다.</p>
     */
    @Test
    @DisplayName("시나리오 9-b: DELETE /api/dlq/business/{dlqId} (없는 항목) → false 반환")
    void Business_DLQ_삭제_대상없음_200() throws Exception {
        log.info("[시나리오 9-b] DELETE /api/dlq/business/{{dlqId}} (없는 항목) → false 검증 시작");

        final String dlqId = "already-deleted-key";

        // given: 이미 삭제된 DLQ 항목을 다시 삭제 시도 (Redis에 해당 키 없음)
        when(businessDlqPort.deleteDlq(dlqId)).thenReturn(false);

        // when + then: 없는 항목 삭제 → 200 + data=false (멱등성 보장)
        mockMvc.perform(delete("/api/dlq/business/{dlqId}", dlqId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false));

        log.info("[시나리오 9-b] Business DLQ 삭제 대상없음(false) 멱등성 확인 완료. dlqId={}", dlqId);
    }
}
