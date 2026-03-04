package com.nori.tc.ui.adapters.web.security;

import com.nori.tc.ui.domain.auth.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Objects;

/**
 * Spring Security 필터 체인 설정 클래스입니다.
 *
 * <p>설정 개요:</p>
 * <ul>
 *   <li>CSRF: 비활성화 (REST API + stateless 세션 사용)</li>
 *   <li>세션: STATELESS (서버 측 세션 사용 안 함)</li>
 *   <li>인증 필터: {@link UiTokenAuthenticationFilter}를
 *       {@link UsernamePasswordAuthenticationFilter} 앞에 등록</li>
 *   <li>공개 경로: POST /auth/login, GET /actuator/health</li>
 *   <li>그 외 경로: 인증 필수 + DB 기반 URL 인가 ({@link UiApiPermissionCache})</li>
 * </ul>
 *
 * <p>URL 인가 로직 ({@link UiApiPermissionCache#isAuthorized}):</p>
 * <ul>
 *   <li>인증되지 않은 요청 → 401 (AuthenticationEntryPoint: HttpServletResponse.SC_UNAUTHORIZED)</li>
 *   <li>인증된 요청 + URI에 매칭 API 권한 없음 → 차단 (closed by default)</li>
 *   <li>인증된 요청 + URI에 매칭 API 권한 있음 → 사용자가 해당 permCode 보유 시 허용</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties
public class UiSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(UiSecurityConfig.class);

    /**
     * Spring Security 필터 체인을 설정합니다.
     *
     * @param http              HttpSecurity 빌더
     * @param tokenFilter       Bearer 토큰 인증 필터
     * @param permissionCache   DB 기반 API 권한 캐시
     * @param authenticationEntryPoint 인증 실패(401) 응답 포맷 통일 엔트리포인트
     * @return 설정된 SecurityFilterChain
     * @throws Exception 설정 오류 시
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final UiTokenAuthenticationFilter tokenFilter,
            final UiApiPermissionCache permissionCache,
            final UiAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        Objects.requireNonNull(tokenFilter, "tokenFilter is null");
        Objects.requireNonNull(permissionCache, "permissionCache is null");
        Objects.requireNonNull(authenticationEntryPoint, "authenticationEntryPoint is null");

        http
                // CSRF 비활성화: REST API + stateless 세션 구조에서는 CSRF 불필요
                .csrf(AbstractHttpConfigurer::disable)

                // 세션 stateless: 서버 측 HttpSession 사용 안 함
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // URL별 인가 설정
                .authorizeHttpRequests(auth -> auth
                        // 공개 경로: 인증 없이 허용
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // 그 외 모든 요청: 인증 필수 + DB 기반 URL 인가
                        .anyRequest().access(buildPermissionAuthorizationManager(permissionCache))
                )

                // UiTokenAuthenticationFilter를 UsernamePasswordAuthenticationFilter 앞에 등록
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)

                // 미인증(Anonymous) 요청 거부 시 401 반환
                // 기본 AuthenticationEntryPoint(Http403ForbiddenEntryPoint)는 403을 반환하므로
                // REST API 표준에 맞게 401로 명시적으로 설정합니다.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                );

        log.info("UiSecurityConfig 초기화 완료. CSRF=비활성, 세션=STATELESS");
        return http.build();
    }

    /**
     * DB 기반 URL 권한 판단 AuthorizationManager를 생성합니다.
     *
     * <p>판단 로직:</p>
     * <ol>
     *   <li>인증 미완료(Anonymous) → 거부 (403)</li>
     *   <li>principal이 {@link UserPrincipal}이 아님 → 거부 (비정상 상태)</li>
     *   <li>{@link UiApiPermissionCache#isAuthorized} 호출 → 결과에 따라 허용/거부</li>
     * </ol>
     *
     * @param permissionCache API 권한 캐시
     * @return AuthorizationManager 인스턴스
     */
    private AuthorizationManager<RequestAuthorizationContext> buildPermissionAuthorizationManager(
            final UiApiPermissionCache permissionCache
    ) {
        return (authenticationSupplier, context) -> {
            final var authentication = authenticationSupplier.get();

            // 인증 미완료 → 거부
            if (authentication == null || !authentication.isAuthenticated()) {
                log.debug("미인증 요청 거부. uri={}", context.getRequest().getRequestURI());
                return new AuthorizationDecision(false);
            }

            // principal 타입 확인
            final Object principal = authentication.getPrincipal();
            if (!(principal instanceof UserPrincipal userPrincipal)) {
                // UiTokenAuthenticationFilter를 거치지 않은 비정상 경로
                log.warn("principal 타입 불일치. principalType={}, uri={}",
                        principal.getClass().getName(), context.getRequest().getRequestURI());
                return new AuthorizationDecision(false);
            }

            // DB 기반 URL 인가 판단
            final String httpMethod = context.getRequest().getMethod();
            final String requestUri = context.getRequest().getRequestURI();
            final boolean authorized = permissionCache.isAuthorized(userPrincipal, httpMethod, requestUri);

            return new AuthorizationDecision(authorized);
        };
    }
}
