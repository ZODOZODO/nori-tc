package com.nori.tc.apps.uibackend.scenario;

import com.nori.tc.ui.domain.auth.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 시나리오 검증: 인증(Authentication) / 인가(Authorization) 흐름 테스트입니다.
 *
 * <p>검증 시나리오:</p>
 * <ol>
 *   <li>POST /auth/login → 200 + token 반환</li>
 *   <li>Bearer token 없이 /api/eqp → 401</li>
 *   <li>유효 token + 권한 없는 API → 403 (권한 캐시에 해당 URI 등록 후 검증)</li>
 *   <li>유효 token + 권한 있는 API → 200 (인증된 사용자 권한 보유)</li>
 *   <li>POST /auth/logout → 200 → 이전 token 재사용 → 401</li>
 * </ol>
 *
 * <p>참고: 권한 캐시(UiApiPermissionCache)는 closed by default 정책으로 동작합니다.
 * 테스트 기본 권한은 {@link UiBackendScenarioTestSupport#setUpCommonMocks()}에서 로드되며,
 * 403 시나리오 검증 시에는 {@link #reloadPermissions(List)}로 재설정합니다.</p>
 */
@DisplayName("Phase 10 시나리오 1~5: 인증/인가 흐름 검증")
class UiAuthScenarioTest extends UiBackendScenarioTestSupport {

    private static final Logger log = LoggerFactory.getLogger(UiAuthScenarioTest.class);

    // ─────────────────────────────────────────────────────────
    // 시나리오 1: POST /auth/login → 200 + token 반환
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 1: 올바른 userId/password 입력 시 세션 토큰이 발급됩니다.
     *
     * <p>검증 포인트:</p>
     * <ul>
     *   <li>HTTP 200 응답</li>
     *   <li>success=true</li>
     *   <li>data.token 비어있지 않음</li>
     *   <li>data.userPk = TEST_USER_PK</li>
     * </ul>
     */
    @Test
    @DisplayName("시나리오 1: 로그인 성공 → 200 + token 반환")
    void 로그인_성공_후_토큰_반환() throws Exception {
        log.info("[시나리오 1] POST /auth/login → 200 + token 반환 검증 시작");

        // given: 사용자 조회 + 비밀번호 검증 Mock 설정
        final String rawPassword = "password123";
        final String fakeHash = "$2a$10$fakeHashForTest";
        when(userPort.findByUserIdNorm(TEST_USER_ID))
                .thenReturn(Optional.of(activeUserInfo(fakeHash)));
        // PasswordVerifierPort Mock: 원문 비밀번호 검증 성공 처리
        when(passwordVerifierPort.matches(rawPassword, fakeHash)).thenReturn(true);
        // sessionPort.save()는 void이므로 별도 stubbing 불필요 (do-nothing 기본값)

        // when + then: 로그인 요청
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"testuser","password":"password123"}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.userPk").value(TEST_USER_PK));

        log.info("[시나리오 1] 로그인 성공 응답 확인 완료");
    }

    /**
     * 시나리오 1-a: 존재하지 않는 사용자 ID로 로그인 시 401을 반환합니다.
     *
     * <p>보안 원칙: 사용자 미존재와 비밀번호 불일치를 동일한 오류 코드로 반환하여
     * 사용자 열거 공격(User Enumeration Attack)을 완화합니다.</p>
     */
    @Test
    @DisplayName("시나리오 1-a: 존재하지 않는 사용자 → 401 반환")
    void 로그인_실패_사용자_없음() throws Exception {
        log.info("[시나리오 1-a] 존재하지 않는 사용자 로그인 시도 검증 시작");

        // given: 사용자 조회 결과 empty (DB에 없는 사용자)
        when(userPort.findByUserIdNorm(anyString())).thenReturn(Optional.empty());

        // when + then: 로그인 시도 → 401
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"nonexistent","password":"anypassword"}
                                """))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[시나리오 1-a] 401 응답 확인 완료");
    }

    /**
     * 시나리오 1-b: 비밀번호 불일치 시 401을 반환합니다.
     */
    @Test
    @DisplayName("시나리오 1-b: 비밀번호 불일치 → 401 반환")
    void 로그인_실패_비밀번호_불일치() throws Exception {
        log.info("[시나리오 1-b] 비밀번호 불일치 로그인 시도 검증 시작");

        // given: 사용자는 존재하지만 비밀번호가 일치하지 않음
        when(userPort.findByUserIdNorm(TEST_USER_ID))
                .thenReturn(Optional.of(activeUserInfo("hashed_pass")));
        when(passwordVerifierPort.matches(anyString(), anyString())).thenReturn(false);

        // when + then: 비밀번호 불일치 → 401
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"testuser","password":"wrongpassword"}
                                """))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[시나리오 1-b] 401 응답 확인 완료");
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 2: Bearer token 없이 보호된 API 호출 → 401
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 2: Authorization 헤더 없이 보호된 API(/api/eqp) 호출 시 401을 반환합니다.
     *
     * <p>UiTokenAuthenticationFilter가 Bearer 토큰을 추출하지 못하면 SecurityContext를
     * 설정하지 않고, 인가 단계에서 Anonymous 요청으로 처리되어 거부됩니다.</p>
     */
    @Test
    @DisplayName("시나리오 2: Bearer token 없이 /api/eqp 호출 → 401")
    void 토큰없이_보호된_API_호출_401() throws Exception {
        log.info("[시나리오 2] Authorization 헤더 없이 보호된 API 호출 검증 시작");

        // when + then: Authorization 헤더 없이 POST /api/eqp → 401
        mockMvc.perform(post("/api/eqp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eqpId":"E001","interfaceType":"HSMS"}
                                """))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[시나리오 2] 401 응답 확인 완료");
    }

    /**
     * 시나리오 2-a: 잘못된 형식의 Bearer 토큰(유효하지 않은 토큰) 사용 시 401 반환.
     *
     * <p>토큰이 Redis 캐시에도 없고 DB 세션에도 없으면 UiAuthenticationException이 발생합니다.</p>
     */
    @Test
    @DisplayName("시나리오 2-a: 유효하지 않은 토큰 → 401 반환")
    void 유효하지않은_토큰_401() throws Exception {
        log.info("[시나리오 2-a] 유효하지 않은 토큰으로 API 호출 검증 시작");

        // given: 캐시 미스 + DB에서도 세션 없음
        when(tokenCachePort.get(anyString())).thenReturn(Optional.empty());
        when(sessionPort.findValidByToken(anyString())).thenReturn(Optional.empty());

        // when + then: 유효하지 않은 토큰 → 401
        mockMvc.perform(post("/api/eqp")
                        .header("Authorization", "Bearer invalid-token-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eqpId":"E001","interfaceType":"HSMS"}
                                """))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[시나리오 2-a] 401 응답 확인 완료");
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 3: 유효 token + 권한 없는 API → 403
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 3: 유효한 토큰이지만 해당 API 권한이 없을 때 403을 반환합니다.
     *
     * <p>권한 캐시에 EQP_MANAGE 권한이 설정된 상태에서
     * 사용자가 해당 권한 코드를 보유하지 않으면 접근이 거부됩니다.</p>
     *
     * <p>테스트 순서:</p>
     * <ol>
     *   <li>apiPermissionPort stubbing: EQP_MANAGE 권한이 /api/eqp에 필요하도록 설정</li>
     *   <li>{@link #reloadPermissions}: UiApiPermissionCache 캐시 강제 갱신</li>
     *   <li>토큰 캐시에서 권한 없는 UserPrincipal 반환</li>
     *   <li>POST /api/eqp → 403 확인</li>
     * </ol>
     */
    @Test
    @DisplayName("시나리오 3: 유효 token + 권한 없는 API → 403")
    void 유효_토큰_권한_없는_API_403() throws Exception {
        log.info("[시나리오 3] 유효 token + 권한 없는 API → 403 검증 시작");

        // given: /api/eqp POST에 EQP_MANAGE 권한 요구 설정 후 캐시 재로딩
        reloadPermissions(List.of(
                apiPermission(EQP_MANAGE_PERM, "/api/eqp", "POST")
        ));

        // 권한 없는 UserPrincipal을 토큰 캐시에서 반환
        final UserPrincipal noPermPrincipal = principalWithNoPermission();
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(noPermPrincipal));

        // when + then: 권한 없는 사용자 → 403
        mockMvc.perform(post("/api/eqp")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eqpId":"E001","interfaceType":"HSMS"}
                                """))
                .andDo(print())
                .andExpect(status().isForbidden());

        log.info("[시나리오 3] 403 응답 확인 완료");
    }

    /**
     * 시나리오 3-b: 권한 캐시 초기화 실패(failsafe) 상태에서는
     * 인증된 사용자라도 보호 API 접근이 전부 차단되어야 합니다.
     *
     * <p>검증 목적:</p>
     * <ul>
     *   <li>UiApiPermissionCache.loadPermissions() 실패 시 initializationFailed=true 전환</li>
     *   <li>isAuthorized()가 즉시 false를 반환하여 403 차단</li>
     *   <li>"권한 로드 실패 시 전체 허용" 회귀(regression) 방지</li>
     * </ul>
     */
    @Test
    @DisplayName("시나리오 3-b: 권한 캐시 초기화 실패(failsafe) 시 보호 API 전면 차단(403)")
    void 권한_캐시_초기화_실패시_보호_API_차단_403() throws Exception {
        log.info("[시나리오 3-b] 권한 캐시 초기화 실패(failsafe) 시 보호 API 차단 검증 시작");

        // given: 권한 캐시 재로딩 시 DB 조회 실패를 강제하여 failsafe 모드로 전환
        when(apiPermissionPort.findAllActiveApiPermissions())
                .thenThrow(new RuntimeException("권한 테이블 조회 실패 시뮬레이션"));
        apiPermissionCache.loadPermissions();

        // 인증은 유효하지만, failsafe 상태에서는 권한과 무관하게 차단되어야 함
        final UserPrincipal principal = principalWithPermission("AUTH_ME_PERM", EQP_MANAGE_PERM);
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(principal));

        // when + then: 보호 API 접근 시 403 반환
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isForbidden());

        log.info("[시나리오 3-b] failsafe 모드 403 차단 확인 완료");
    }

    /**
     * 시나리오 3-c: 권한 테이블에 등록되지 않은 API는 기본 차단(closed by default)되어야 합니다.
     *
     * <p>검증 목적:</p>
     * <ul>
     *   <li>매칭 권한이 없는 URI 요청을 403으로 차단</li>
     *   <li>신규 API 추가 시 권한 등록 누락을 즉시 탐지</li>
     * </ul>
     */
    @Test
    @DisplayName("시나리오 3-c: 권한 미등록 API는 기본 차단(403)")
    void 권한_미등록_API_기본차단_403() throws Exception {
        log.info("[시나리오 3-c] 권한 미등록 API 기본 차단(403) 검증 시작");

        // given: /api/eqp 권한만 로드하여 /api/dlq/* 는 '미등록 API' 상태를 만듦
        reloadPermissions(List.of(
                apiPermission(EQP_MANAGE_PERM, "/api/eqp", null)
        ));

        // 사용자에게 EQP_MANAGE 권한이 있어도, /api/dlq는 매핑이 없으므로 차단되어야 함
        final UserPrincipal principal = principalWithPermission(EQP_MANAGE_PERM);
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(principal));

        // when + then: 매핑 누락 API 접근 시 403
        mockMvc.perform(get("/api/dlq/gateway")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isForbidden());

        log.info("[시나리오 3-c] 미등록 API 403 차단 확인 완료");
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 4: 유효 token + 권한 있는 API → 200
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 4: 유효한 토큰 + EQP_MANAGE 권한 보유 시 GET /auth/me → 200을 반환합니다.
     *
     * <p>권한 캐시 정책은 closed by default 이며,
     * 사용자가 해당 권한을 보유한 경우에만 접근이 허용됩니다.</p>
     */
    @Test
    @DisplayName("시나리오 4: 유효 token + 권한 있는 API(GET /auth/me) → 200")
    void 유효_토큰_권한_있는_API_200() throws Exception {
        log.info("[시나리오 4] 유효 token + 권한 있는 API → 200 검증 시작");

        // given: /auth/me에 권한 요구 설정 + 사용자가 해당 권한 보유
        reloadPermissions(List.of(
                apiPermission("AUTH_ME_PERM", "/auth/me", "GET")
        ));

        // 해당 권한을 보유한 UserPrincipal 반환
        final UserPrincipal authMePrincipal = new UserPrincipal(
                TEST_USER_PK, TEST_USER_ID, Set.of("AUTH_ME_PERM")
        );
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(authMePrincipal));

        // when + then: 권한 보유 사용자 → 200
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userPk").value(TEST_USER_PK))
                .andExpect(jsonPath("$.data.userId").value(TEST_USER_ID));

        log.info("[시나리오 4] 200 응답 확인 완료");
    }

    // ─────────────────────────────────────────────────────────
    // 시나리오 5: POST /auth/logout → 200 → 이전 token 재사용 → 401
    // ─────────────────────────────────────────────────────────

    /**
     * 시나리오 5: 로그아웃 후 이전 토큰으로 API 재호출 시 401을 반환합니다.
     *
     * <p>검증 흐름:</p>
     * <ol>
     *   <li>유효한 토큰으로 POST /auth/logout → 200</li>
     *   <li>LogoutUseCase: sessionPort.revoke() + tokenCachePort.evict() 호출 확인</li>
     *   <li>폐기 후 동일 토큰으로 GET /auth/me 재호출:
     *       캐시 evict + DB 세션 폐기 시뮬레이션 → 401</li>
     * </ol>
     */
    @Test
    @DisplayName("시나리오 5: 로그아웃 후 이전 token 재사용 → 401")
    void 로그아웃_후_이전_토큰_재사용_401() throws Exception {
        log.info("[시나리오 5] 로그아웃 후 이전 token 재사용 → 401 검증 시작");

        // given: 1단계 - 로그아웃 전 유효한 토큰 상태 설정
        final UserPrincipal principal = principalWithPermission("AUTH_LOGOUT_PERM");
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(principal));

        // when: POST /auth/logout → 200 확인
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // then: LogoutUseCase가 sessionPort.revoke() + tokenCachePort.evict() 호출했는지 확인
        verify(sessionPort).revoke(TEST_TOKEN);
        verify(tokenCachePort).evict(TEST_TOKEN);

        log.info("[시나리오 5] 로그아웃 완료 확인. sessionPort.revoke + tokenCachePort.evict 호출됨");

        // given: 2단계 - 폐기된 토큰 상태 시뮬레이션 (캐시 miss + DB 세션 없음)
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.empty());
        when(sessionPort.findValidByToken(TEST_TOKEN)).thenReturn(Optional.empty());

        // when + then: 폐기된 토큰으로 재호출 → 401
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isUnauthorized());

        log.info("[시나리오 5] 로그아웃 후 이전 token 재사용 → 401 확인 완료");
    }

    /**
     * 시나리오 5-a: Redis evict 실패가 발생해도 로그아웃 응답은 200을 유지합니다.
     *
     * <p>DB revoke가 성공한 상태에서 Redis 캐시 삭제만 실패할 수 있으므로,
     * LogoutUseCase는 예외를 전파하지 않고 ERROR 로그만 남긴 뒤 정상 흐름을 유지해야 합니다.</p>
     */
    @Test
    @DisplayName("시나리오 5-a: 로그아웃 Redis evict 실패 → 200 유지")
    void 로그아웃_redis_evict_실패_응답_200() throws Exception {
        log.info("[시나리오 5-a] 로그아웃 Redis evict 실패 시 200 유지 검증 시작");

        final UserPrincipal principal = principalWithPermission("AUTH_LOGOUT_PERM");
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(principal));
        doThrow(new RuntimeException("Redis write timeout"))
                .when(tokenCachePort).evict(TEST_TOKEN);

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionPort).revoke(TEST_TOKEN);
        verify(tokenCachePort).evict(TEST_TOKEN);
        log.info("[시나리오 5-a] Redis evict 실패 시에도 로그아웃 200 유지 확인 완료");
    }

    /**
     * 시나리오 5-b: lastSeenAt 업데이트가 실패해도 인증 자체는 성공해야 합니다.
     *
     * <p>ValidateTokenUseCase는 lastSeenAt 업데이트 예외를 비동기로 격리하고 WARN으로만
     * 기록해야 하며, 인증 요청의 HTTP 응답을 실패시키면 안 됩니다.</p>
     */
    @Test
    @DisplayName("시나리오 5-b: lastSeenAt 업데이트 실패 시에도 /auth/me 200 유지")
    void lastSeenAt_업데이트_실패해도_인증_성공_200() throws Exception {
        log.info("[시나리오 5-b] lastSeenAt 업데이트 실패 격리 검증 시작");

        reloadPermissions(List.of(
                apiPermission("AUTH_ME_PERM", "/auth/me", "GET")
        ));

        // 캐시 미스 경로 강제
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.empty());
        when(sessionPort.findValidByToken(TEST_TOKEN)).thenReturn(Optional.of(
                new com.nori.tc.db.domain.user.TcUiAuthSession(
                        TEST_TOKEN,
                        TEST_USER_PK,
                        OffsetDateTime.now(),
                        OffsetDateTime.now().plusHours(8),
                        null,
                        false
                )
        ));
        when(userPort.findByUserPk(TEST_USER_PK)).thenReturn(Optional.of(activeUserInfo("fake-hash")));
        when(permissionPort.findPermissionCodesByUserPk(TEST_USER_PK)).thenReturn(Set.of("AUTH_ME_PERM"));
        doThrow(new RuntimeException("DB timeout on updateLastSeenAt"))
                .when(sessionPort).updateLastSeenAt(org.mockito.ArgumentMatchers.eq(TEST_TOKEN), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userPk").value(TEST_USER_PK));

        log.info("[시나리오 5-b] lastSeenAt 실패 분리 처리 확인 완료");
    }

    /**
     * 시나리오 5-c: credentials 타입이 String이 아니면 로그아웃은 401로 종료되어야 합니다.
     *
     * <p>AuthController.logout()의 안전 캐스팅 패턴 검증입니다.</p>
     */
    @Test
    @DisplayName("시나리오 5-c: /auth/logout credentials 타입 불일치 → 401")
    void 로그아웃_credentials_타입불일치_401() throws Exception {
        log.info("[시나리오 5-c] /auth/logout credentials 타입 불일치 401 검증 시작");

        reloadPermissions(List.of(
                apiPermission("AUTH_LOGOUT_PERM", "/auth/logout", "POST")
        ));

        final UserPrincipal principal = new UserPrincipal(
                TEST_USER_PK,
                TEST_USER_ID,
                Set.of("AUTH_LOGOUT_PERM")
        );
        final UsernamePasswordAuthenticationToken malformedAuthentication =
                new UsernamePasswordAuthenticationToken(principal, 12345, Collections.emptyList());

        mockMvc.perform(post("/auth/logout")
                        .with(authentication(malformedAuthentication)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[시나리오 5-c] 401 응답 확인 완료");
    }

    /**
     * 시나리오 5-d: 비인증(Anonymous) 요청의 /auth/logout 은 500이 아닌 401이어야 합니다.
     */
    @Test
    @DisplayName("시나리오 5-d: Anonymous /auth/logout → 401")
    void Anonymous_로그아웃_401() throws Exception {
        log.info("[시나리오 5-d] Anonymous /auth/logout 401 검증 시작");

        mockMvc.perform(post("/auth/logout"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[시나리오 5-d] 401 응답 확인 완료");
    }

    /**
     * 시나리오 5-e: 비인증(Anonymous) 요청의 /auth/me 는 500이 아닌 401이어야 합니다.
     */
    @Test
    @DisplayName("시나리오 5-e: Anonymous /auth/me → 401")
    void Anonymous_내정보조회_401() throws Exception {
        log.info("[시나리오 5-e] Anonymous /auth/me 401 검증 시작");

        mockMvc.perform(get("/auth/me"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[시나리오 5-e] 401 응답 확인 완료");
    }
}
