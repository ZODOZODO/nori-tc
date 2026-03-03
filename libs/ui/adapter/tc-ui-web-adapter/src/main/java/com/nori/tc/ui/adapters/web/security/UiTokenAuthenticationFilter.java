package com.nori.tc.ui.adapters.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.ui.adapters.web.dto.response.ApiResponse;
import com.nori.tc.ui.core.exception.UiAuthenticationException;
import com.nori.tc.ui.core.usecase.ValidateTokenUseCase;
import com.nori.tc.ui.domain.auth.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Bearer 토큰 기반 인증 처리 필터입니다.
 *
 * <p>처리 흐름:</p>
 * <ol>
 *   <li>{@code Authorization: Bearer {token}} 헤더에서 토큰 추출</li>
 *   <li>토큰 없음 → SecurityContext 미설정 후 다음 필터로 통과
 *       (인가 단계에서 익명 요청으로 처리)</li>
 *   <li>{@link ValidateTokenUseCase#execute(String)} 호출:
 *       <ul>
 *         <li>Business Redis 캐시 히트 → 캐시된 UserPrincipal 사용 (DB 미조회)</li>
 *         <li>캐시 미스 → DB 조회 (session + user + permissions) → 캐시 저장</li>
 *         <li>lastSeenAt 업데이트 (캐시 미스 시, 동기 처리)</li>
 *       </ul>
 *   </li>
 *   <li>토큰 검증 성공 → {@code UsernamePasswordAuthenticationToken}을 SecurityContext에 등록
 *       <ul>
 *         <li>principal = {@link UserPrincipal} (userPk, userId, permissionCodes)</li>
 *         <li>credentials = 원본 Bearer 토큰 (LogoutUseCase에서 토큰 폐기 시 사용)</li>
 *       </ul>
 *   </li>
 *   <li>{@link UiAuthenticationException} → 401 Unauthorized JSON 응답 즉시 반환</li>
 * </ol>
 *
 * <p>NOTE: lastSeenAt 비동기 업데이트는 향후 성능 개선 시 적용 가능합니다.
 * ValidateTokenUseCase의 주석 참고 ("어댑터에서 @Async 적용 가능").</p>
 */
@Component
public class UiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UiTokenAuthenticationFilter.class);

    /** Bearer 토큰 접두사 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final ValidateTokenUseCase validateTokenUseCase;
    private final ObjectMapper objectMapper;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param validateTokenUseCase 토큰 검증 유스케이스 (캐시 → DB 폴백 처리)
     * @param objectMapper         401 응답 JSON 직렬화용
     */
    public UiTokenAuthenticationFilter(
            final ValidateTokenUseCase validateTokenUseCase,
            final ObjectMapper objectMapper
    ) {
        this.validateTokenUseCase = Objects.requireNonNull(validateTokenUseCase, "validateTokenUseCase is null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
    }

    /**
     * DeferredResult 비동기 디스패치에서도 토큰 인증 필터를 실행합니다.
     *
     * <p>Spring MVC의 {@code DeferredResult} 완료 시, 서블릿 컨테이너는
     * 응답 전송을 위해 요청을 재디스패치(ASYNC dispatch)합니다.
     * {@link org.springframework.web.filter.OncePerRequestFilter}의 기본 동작은
     * ASYNC 디스패치에서 필터를 건너뛰므로({@code shouldNotFilterAsyncDispatch() = true}),
     * SecurityContext가 비어 있어 Spring Security가 401을 반환하게 됩니다.</p>
     *
     * <p>{@code false}를 반환하여 ASYNC 디스패치 시에도 이 필터가 실행되도록 합니다.
     * 재실행 시 Authorization 헤더에서 토큰을 재검증하고 SecurityContext를 복원합니다.
     * 토큰 검증은 Redis 캐시 히트로 처리되므로 성능 영향이 최소화됩니다.</p>
     *
     * @return false — ASYNC 디스패치에서도 이 필터를 실행함
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * 요청당 1회 실행되는 인증 처리 메서드입니다.
     *
     * @param request     HTTP 요청
     * @param response    HTTP 응답
     * @param filterChain 다음 필터 체인
     */
    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {

        // 1단계: Authorization 헤더에서 Bearer 토큰 추출
        final String token = extractBearerToken(request);
        if (token == null) {
            // 토큰 없음 → SecurityContext 미설정 후 다음 필터로 통과
            // 공개 경로(POST /auth/login 등)는 이 경로로 처리됨
            log.trace("Bearer 토큰 없음. uri={}, method={}", request.getRequestURI(), request.getMethod());
            filterChain.doFilter(request, response);
            return;
        }

        // 2단계: 토큰 검증 (Business Redis 캐시 → DB 폴백)
        final UserPrincipal principal;
        try {
            principal = validateTokenUseCase.execute(token);
        } catch (UiAuthenticationException e) {
            // 토큰 유효하지 않음 (만료/폐기/미존재/계정 비활성)
            log.warn("토큰 인증 실패. uri={}, method={}, reason={}",
                    request.getRequestURI(), request.getMethod(), e.getMessage());
            sendUnauthorized(response, "유효하지 않은 인증 토큰입니다.");
            return;
        } catch (Exception e) {
            // 예상치 못한 예외 (Redis/DB 장애 등)
            log.error("토큰 검증 중 예상치 못한 오류. uri={}, method={}",
                    request.getRequestURI(), request.getMethod(), e);
            sendUnauthorized(response, "인증 처리 중 오류가 발생했습니다.");
            return;
        }

        // 3단계: SecurityContext에 인증 정보 등록
        // credentials에 원본 토큰을 보관 → LogoutUseCase에서 토큰 폐기 시 사용
        final UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, token, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("인증 처리 완료. userPk={}, userId={}, uri={}, method={}",
                principal.userPk(), principal.userId(),
                request.getRequestURI(), request.getMethod());

        // 4단계: 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // 내부 유틸
    // -------------------------------------------------------------------------

    /**
     * Authorization 헤더에서 Bearer 토큰을 추출합니다.
     *
     * @param request HTTP 요청
     * @return Bearer 토큰 문자열 (없거나 형식이 맞지 않으면 null)
     */
    private static String extractBearerToken(final HttpServletRequest request) {
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        final String token = header.substring(BEARER_PREFIX.length()).strip();
        return token.isEmpty() ? null : token;
    }

    /**
     * 401 Unauthorized JSON 응답을 즉시 반환합니다.
     *
     * <p>Spring Security의 인증 실패 응답과 일관된 형태({@link ApiResponse})로 반환합니다.</p>
     *
     * @param response HTTP 응답
     * @param message  사용자 친화적 오류 메시지
     */
    private void sendUnauthorized(final HttpServletResponse response, final String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        final String body = objectMapper.writeValueAsString(
                ApiResponse.error("UNAUTHORIZED", message)
        );
        response.getWriter().write(body);
        response.getWriter().flush();
    }
}
