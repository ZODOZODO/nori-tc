package com.nori.tc.apps.uibackend.scenario;

import com.nori.tc.ui.domain.auth.UserPrincipal;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4(테스트 갱신) 기준의 인증/보안 시나리오 테스트입니다.
 *
 * <p>핵심 검증 범위:</p>
 * <ul>
 *   <li>로그인 응답 본문에서 {@code data.token} 제거 계약 검증</li>
 *   <li>로그인/로그아웃 응답의 {@code Set-Cookie} 계약 검증</li>
 *   <li>쿠키 기반 인증(쿠키 없음 401, 쿠키 있음 200) 검증</li>
 *   <li>로그아웃 후 인증 쿠키 재사용 불가 검증</li>
 *   <li>CSRF 누락 시 403, 유효 CSRF 포함 시 정상 처리 검증</li>
 * </ul>
 */
@DisplayName("Phase 4: Cookie 인증/CSRF 시나리오 검증")
class UiAuthScenarioTest extends UiBackendScenarioTestSupport {

    private static final Logger log = LoggerFactory.getLogger(UiAuthScenarioTest.class);

    /**
     * /error는 내부 에러 디스패치의 최종 응답 포인트이므로,
     * 인증 필터에서 401로 덮어쓰지 않고 원래 에러 상태를 전달해야 합니다.
     */
    @Test
    @DisplayName("인증 0: /error 경로는 401로 치환되지 않아야 함")
    void error_경로_응답_상태_401_치환_방지() throws Exception {
        log.info("[인증 0] /error 응답 상태 401 치환 방지 검증 시작");

        final MvcResult result = mockMvc.perform(get("/error"))
                .andDo(print())
                .andReturn();

        assertNotEquals(401, result.getResponse().getStatus(),
                "/error는 보안 필터에서 401로 치환되면 안 됩니다.");

        log.info("[인증 0] /error 응답 상태 401 치환 방지 검증 완료. status={}", result.getResponse().getStatus());
    }

    /**
     * 로그인 성공 시 응답 본문에는 토큰이 노출되지 않고,
     * 인증 쿠키만 {@code Set-Cookie} 헤더로 내려가야 합니다.
     */
    @Test
    @DisplayName("인증 1: 로그인 응답 token 미포함 + Set-Cookie 발급")
    void 로그인_응답_token_미포함_및_SetCookie_검증() throws Exception {
        log.info("[인증 1] 로그인 응답 계약(token 제거 + Set-Cookie) 검증 시작");

        // given: 로그인에 필요한 사용자 조회/비밀번호 검증 성공 시나리오를 설정합니다.
        final String rawPassword = "password123";
        final String fakeHash = "$2a$10$fakeHashForTest";
        when(userPort.findByUserIdNorm(TEST_USER_ID)).thenReturn(Optional.of(activeUserInfo(fakeHash)));
        when(passwordVerifierPort.matches(rawPassword, fakeHash)).thenReturn(true);

        // when: 로그인 요청을 전송합니다.
        final MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"testuser","password":"password123"}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userPk").value(TEST_USER_PK))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andReturn();

        // then: 응답 헤더의 Set-Cookie에서 인증 쿠키 발급 계약을 검증합니다.
        final List<String> setCookieHeaders = loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        final String authCookiePrefix = authProperties.cookieName() + "=";
        final String authSetCookie = setCookieHeaders.stream()
                .filter(header -> header.startsWith(authCookiePrefix))
                .findFirst()
                .orElse(null);

        assertNotNull(authSetCookie, "로그인 응답에 인증 Set-Cookie 헤더가 존재해야 합니다.");
        assertAll(
                () -> assertTrue(authSetCookie.contains("HttpOnly"),
                        "인증 쿠키는 JavaScript 접근 차단을 위해 HttpOnly 여야 합니다."),
                () -> assertTrue(authSetCookie.contains("Path=" + authProperties.cookiePath()),
                        "인증 쿠키 Path는 설정값과 동일해야 합니다."),
                () -> assertTrue(authSetCookie.contains("SameSite=" + authProperties.cookieSameSite()),
                        "인증 쿠키 SameSite는 설정값과 동일해야 합니다."),
                () -> assertTrue(authSetCookie.contains("Max-Age="),
                        "인증 쿠키는 만료 정책이 포함되어야 합니다.")
        );

        log.info("[인증 1] 로그인 응답 token 제거 및 인증 쿠키 발급 계약 검증 완료");
    }

    /**
     * 보호 API를 인증 쿠키 없이 호출하면 401을 반환해야 합니다.
     */
    @Test
    @DisplayName("인증 2: 인증 쿠키 없는 보호 API 호출 시 401")
    void 쿠키없는_보호_API_호출_401() throws Exception {
        log.info("[인증 2] 인증 쿠키 없는 보호 API 호출 401 검증 시작");

        mockMvc.perform(get("/api/auth/me"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[인증 2] 401 응답 검증 완료");
    }

    /**
     * 보호 API에 유효한 인증 쿠키가 포함되면 인증/인가가 통과되어야 합니다.
     */
    @Test
    @DisplayName("인증 3: 유효한 인증 쿠키 포함 보호 API 호출 시 200")
    void 쿠키있는_보호_API_인증_통과_200() throws Exception {
        log.info("[인증 3] 유효 인증 쿠키 기반 /api/auth/me 200 검증 시작");

        // given: /api/auth/me 경로 권한을 로드하고, 토큰 캐시에서 권한 보유 principal을 반환합니다.
        reloadPermissions(List.of(
                apiPermission("AUTH_ME_PERM", "/api/auth/me", "GET")
        ));
        final UserPrincipal principal = principalWithPermission("AUTH_ME_PERM");
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(principal));

        mockMvc.perform(get("/api/auth/me")
                        .cookie(authCookie()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userPk").value(TEST_USER_PK))
                .andExpect(jsonPath("$.data.userId").value(TEST_USER_ID));

        log.info("[인증 3] 인증 쿠키 포함 보호 API 200 검증 완료");
    }

    /**
     * 로그아웃이 성공하면 삭제 쿠키를 내려야 하며,
     * 동일 쿠키 재사용 요청은 401로 거부되어야 합니다.
     */
    @Test
    @DisplayName("인증 4: 로그아웃 후 쿠키 삭제 + 동일 쿠키 재사용 불가")
    void 로그아웃_후_쿠키_삭제_및_재사용_불가() throws Exception {
        log.info("[인증 4] 로그아웃 쿠키 삭제 및 재사용 불가 검증 시작");

        // given: 로그아웃 권한을 가진 principal을 토큰 캐시에 연결합니다.
        final UserPrincipal principal = principalWithPermission("AUTH_LOGOUT_PERM");
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(principal));

        // when: 유효 CSRF와 인증 쿠키를 함께 전달해 로그아웃을 수행합니다.
        final MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(authCookie()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        // then: 로그아웃은 삭제 쿠키(Max-Age=0)를 내려야 하며 서버 세션/캐시 폐기를 수행해야 합니다.
        final List<String> setCookieHeaders = logoutResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        final String authCookiePrefix = authProperties.cookieName() + "=";
        final String deleteCookieHeader = setCookieHeaders.stream()
                .filter(header -> header.startsWith(authCookiePrefix))
                .findFirst()
                .orElse(null);
        assertNotNull(deleteCookieHeader, "로그아웃 응답에 인증 쿠키 삭제 헤더가 존재해야 합니다.");
        assertAll(
                () -> assertTrue(deleteCookieHeader.contains("Max-Age=0"),
                        "로그아웃 시 인증 쿠키는 즉시 만료(Max-Age=0)되어야 합니다."),
                () -> assertTrue(deleteCookieHeader.contains("HttpOnly"),
                        "삭제 쿠키도 동일한 보안 속성(HttpOnly)을 유지해야 합니다.")
        );
        verify(sessionPort).revoke(TEST_TOKEN);
        verify(tokenCachePort).evict(TEST_TOKEN);

        // given: 로그아웃 이후 동일 토큰은 캐시/DB 모두 무효로 간주되도록 설정합니다.
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.empty());
        when(sessionPort.findValidByToken(TEST_TOKEN)).thenReturn(Optional.empty());

        // then: 동일 쿠키 재사용 요청은 401이어야 합니다.
        mockMvc.perform(get("/api/auth/me")
                        .cookie(authCookie()))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        log.info("[인증 4] 로그아웃 쿠키 삭제 및 재사용 불가 검증 완료");
    }

    /**
     * CSRF 토큰 발급 엔드포인트는 프런트와 합의한 쿠키 계약을 항상 만족해야 합니다.
     *
     * <p>특히 Secure/SameSite/Domain/Path 정책은 인증 쿠키와 동일해야 하므로,
     * 환경별 설정(UiAuthProperties)이 CSRF 쿠키에도 반영되는지 검증합니다.</p>
     */
    @Test
    @DisplayName("CSRF 0: /api/auth/csrf 호출 시 XSRF-TOKEN Set-Cookie 발급")
    void csrf_발급_엔드포인트_SetCookie_검증() throws Exception {
        log.info("[CSRF 0] /api/auth/csrf Set-Cookie 발급 계약 검증 시작");

        final MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        final List<String> setCookieHeaders = csrfResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        final String csrfCookiePrefix = authProperties.csrfCookieName() + "=";
        final String csrfSetCookie = setCookieHeaders.stream()
                .filter(header -> header.startsWith(csrfCookiePrefix))
                .findFirst()
                .orElse(null);
        final Cookie csrfCookie = java.util.Arrays.stream(csrfResult.getResponse().getCookies())
                .filter(cookie -> authProperties.csrfCookieName().equals(cookie.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(csrfSetCookie, "CSRF 발급 응답에는 XSRF-TOKEN Set-Cookie가 포함되어야 합니다.");
        assertNotNull(csrfCookie, "CSRF 발급 응답에는 XSRF-TOKEN 쿠키 객체가 포함되어야 합니다.");
        assertAll(
                () -> assertEquals(authProperties.cookiePath(), csrfCookie.getPath(),
                        "CSRF 쿠키 Path는 인증 쿠키 Path와 동일해야 합니다."),
                () -> assertEquals(authProperties.cookieSameSite(), csrfCookie.getAttribute("SameSite"),
                        "CSRF 쿠키 SameSite는 인증 쿠키 SameSite와 동일해야 합니다."),
                () -> assertFalse(csrfCookie.isHttpOnly(),
                        "CSRF 쿠키는 프런트(JavaScript)에서 읽을 수 있어야 하므로 HttpOnly=false여야 합니다."),
                () -> {
                    if (authProperties.cookieSecure()) {
                        assertTrue(csrfCookie.getSecure(),
                                "cookieSecure=true 환경에서는 CSRF 쿠키도 Secure 속성을 가져야 합니다.");
                    } else {
                        assertFalse(csrfCookie.getSecure(),
                                "cookieSecure=false 환경에서는 CSRF 쿠키에 Secure 속성이 없어야 합니다.");
                    }
                },
                () -> {
                    if (authProperties.cookieDomain() != null) {
                        assertEquals(authProperties.cookieDomain(), csrfCookie.getDomain(),
                                "cookieDomain이 지정된 경우 CSRF 쿠키에도 동일 Domain이 반영되어야 합니다.");
                    }
                }
        );

        log.info("[CSRF 0] /api/auth/csrf Set-Cookie 발급 계약 검증 완료");
    }

    /**
     * 상태 변경 요청에서 CSRF가 누락되면 403을 반환해야 합니다.
     */
    @Test
    @DisplayName("CSRF 1: 상태 변경 요청에서 CSRF 누락 시 403")
    void 상태변경_요청_CSRF_누락_403() throws Exception {
        log.info("[CSRF 1] 상태 변경 요청 CSRF 누락 403 검증 시작");

        // given: 인증 쿠키는 존재하지만 CSRF 헤더/쿠키는 의도적으로 생략합니다.
        final UserPrincipal principal = principalWithPermission("AUTH_LOGOUT_PERM");
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(principal));

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(authCookie()))
                .andDo(print())
                .andExpect(status().isForbidden());

        // then: CSRF 단계에서 차단되므로 비즈니스 로그아웃 유스케이스는 실행되면 안 됩니다.
        verify(sessionPort, never()).revoke(anyString());
        verify(tokenCachePort, never()).evict(anyString());

        log.info("[CSRF 1] CSRF 누락 403 및 비즈니스 미호출 검증 완료");
    }

    /**
     * 유효한 CSRF 쿠키/헤더를 포함하면 상태 변경 요청이 정상 처리되어야 합니다.
     */
    @Test
    @DisplayName("CSRF 2: 유효한 CSRF 포함 시 상태 변경 요청 정상 처리")
    void 유효_CSRF_포함_상태변경_정상처리() throws Exception {
        log.info("[CSRF 2] 유효 CSRF 포함 상태 변경 요청 정상 처리 검증 시작");

        final UserPrincipal principal = principalWithPermission("AUTH_LOGOUT_PERM");
        when(tokenCachePort.get(TEST_TOKEN)).thenReturn(Optional.of(principal));

        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(authCookie()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionPort).revoke(TEST_TOKEN);
        verify(tokenCachePort).evict(TEST_TOKEN);
        log.info("[CSRF 2] 유효 CSRF 포함 정상 처리 검증 완료");
    }

}
