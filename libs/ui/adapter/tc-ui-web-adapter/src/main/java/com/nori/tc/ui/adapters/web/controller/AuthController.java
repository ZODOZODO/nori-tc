package com.nori.tc.ui.adapters.web.controller;

import com.nori.tc.ui.adapters.web.dto.request.LoginRequest;
import com.nori.tc.ui.adapters.web.dto.response.ApiResponse;
import com.nori.tc.ui.adapters.web.dto.response.LoginResponse;
import com.nori.tc.ui.adapters.web.dto.response.MeResponse;
import com.nori.tc.ui.core.exception.UiAuthenticationException;
import com.nori.tc.ui.core.usecase.LoginUseCase;
import com.nori.tc.ui.core.usecase.LogoutUseCase;
import com.nori.tc.ui.domain.auth.AuthToken;
import com.nori.tc.ui.domain.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 인증(Authentication) REST API 컨트롤러입니다.
 *
 * <p>제공 엔드포인트:</p>
 * <ul>
 *   <li>POST /auth/login  — 사용자 ID/비밀번호로 로그인, 세션 토큰 발급</li>
 *   <li>POST /auth/logout — 현재 세션 토큰 폐기 (로그아웃)</li>
 *   <li>GET  /auth/me     — 현재 인증된 사용자 정보 조회</li>
 * </ul>
 *
 * <p>인증 흐름:</p>
 * <p>로그인 성공 시 발급된 토큰을 이후 API 요청의
 * {@code Authorization: Bearer {token}} 헤더에 포함하여 사용합니다.
 * 로그아웃 시 SecurityContext의 credentials에서 원본 토큰을 추출하여 폐기합니다.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param loginUseCase  로그인(세션 토큰 발급) 유스케이스
     * @param logoutUseCase 로그아웃(세션 토큰 폐기) 유스케이스
     */
    public AuthController(
            final LoginUseCase loginUseCase,
            final LogoutUseCase logoutUseCase
    ) {
        this.loginUseCase = Objects.requireNonNull(loginUseCase, "loginUseCase is null");
        this.logoutUseCase = Objects.requireNonNull(logoutUseCase, "logoutUseCase is null");
    }

    /**
     * 사용자 ID와 비밀번호로 로그인하여 세션 토큰을 발급합니다.
     *
     * <p>UiSecurityConfig에서 이 경로는 permitAll()로 공개 접근을 허용합니다.
     * 인증 성공 시 64자 alphanumeric 세션 토큰을 반환합니다.</p>
     *
     * @param request 로그인 요청 DTO (userId, password 필수)
     * @return 200 OK + 발급된 토큰 정보 | 401 인증 실패
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody final LoginRequest request
    ) {
        log.info("로그인 요청. userId={}", request.userId());

        try {
            final AuthToken authToken = loginUseCase.execute(request.userId(), request.password());

            final LoginResponse response = new LoginResponse(
                    authToken.token(),
                    authToken.userPk(),
                    authToken.issuedAt(),
                    authToken.expiresAt()
            );

            log.info("로그인 성공. userId={}, userPk={}, expiresAt={}",
                    request.userId(), authToken.userPk(), authToken.expiresAt());

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (UiAuthenticationException e) {
            // 사용자 미존재, 비밀번호 불일치, 계정 비활성 등 인증 실패
            log.warn("로그인 실패. userId={}, reason={}", request.userId(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", e.getMessage()));
        }
    }

    /**
     * 현재 세션 토큰을 폐기하여 로그아웃 처리합니다.
     *
     * <p>UiTokenAuthenticationFilter가 SecurityContext의 credentials에 원본 Bearer 토큰을
     * 저장해 두므로, 요청 헤더를 다시 파싱할 필요 없이 직접 추출합니다.
     * DB와 Redis 양쪽에서 토큰을 즉시 무효화합니다.</p>
     *
     * @return 200 OK (이미 폐기된 토큰이어도 멱등성 보장)
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // UiTokenAuthenticationFilter에서 credentials에 원본 토큰 보관 (LogoutUseCase용)
        final String token = (String) authentication.getCredentials();
        final String tokenPrefix = token.substring(0, Math.min(8, token.length()));

        log.info("로그아웃 요청. token={}..., userPk={}",
                tokenPrefix, ((UserPrincipal) authentication.getPrincipal()).userPk());

        logoutUseCase.execute(token);

        // SecurityContext 초기화: 이후 필터 체인에서 인증 정보 노출 방지
        SecurityContextHolder.clearContext();

        log.info("로그아웃 완료. token={}...", tokenPrefix);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 현재 인증된 사용자의 식별 정보와 권한 목록을 반환합니다.
     *
     * <p>SecurityContext의 principal에서 UserPrincipal을 추출합니다.
     * 클라이언트에서 메뉴 노출 여부나 버튼 활성화 판단에 활용합니다.</p>
     *
     * @return 200 OK + 현재 사용자 정보 (userPk, userId, permissionCodes)
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // UiTokenAuthenticationFilter에서 principal을 UserPrincipal로 설정
        final UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        log.debug("현재 사용자 정보 조회. userPk={}, userId={}",
                principal.userPk(), principal.userId());

        final MeResponse response = new MeResponse(
                principal.userPk(),
                principal.userId(),
                principal.permissionCodes()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
