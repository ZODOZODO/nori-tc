package com.nori.tc.apps.uibackend.scenario;

import com.nori.tc.db.domain.common.user.TcUiPermissionMatchType;
import com.nori.tc.db.domain.common.user.TcUiPermissionResourceType;
import com.nori.tc.db.domain.common.user.UserStatus;
import com.nori.tc.db.domain.user.TcUiAuthSession;
import com.nori.tc.db.domain.user.TcUiPermission;
import com.nori.tc.db.domain.user.TcUserInfo;
import com.nori.tc.ui.adapters.web.config.UiDualRequestProperties;
import com.nori.tc.ui.adapters.web.controller.AsyncResultController;
import com.nori.tc.ui.adapters.web.controller.AuthController;
import com.nori.tc.ui.adapters.web.controller.DlqController;
import com.nori.tc.ui.adapters.web.controller.EqpController;
import com.nori.tc.ui.adapters.web.security.UiApiPermissionCache;
import com.nori.tc.ui.adapters.web.security.UiSecurityConfig;
import com.nori.tc.ui.adapters.web.security.UiTokenAuthenticationFilter;
import com.nori.tc.ui.core.port.db.PasswordVerifierPort;
import com.nori.tc.ui.core.port.db.PermissionPort;
import com.nori.tc.ui.core.port.db.SessionPort;
import com.nori.tc.ui.core.port.db.UiApiPermissionPort;
import com.nori.tc.ui.core.port.db.UserPort;
import com.nori.tc.ui.core.port.messaging.UiBusinessEventPublishPort;
import com.nori.tc.ui.core.port.messaging.UiGatewayEqpRoutePartitionLookupPort;
import com.nori.tc.ui.core.port.messaging.UiGatewayEventPublishPort;
import com.nori.tc.ui.core.port.redis.AsyncResultStorePort;
import com.nori.tc.ui.core.port.redis.BusinessDlqPort;
import com.nori.tc.ui.core.port.redis.DualResponseRedisPort;
import com.nori.tc.ui.core.port.redis.GatewayDlqPort;
import com.nori.tc.ui.core.port.redis.TokenCachePort;
import com.nori.tc.ui.core.properties.UiAuthProperties;
import com.nori.tc.ui.core.registry.DualResponseRegistry;
import com.nori.tc.ui.core.service.UiCommandIngressService;
import com.nori.tc.ui.core.usecase.LoginUseCase;
import com.nori.tc.ui.core.usecase.LogoutUseCase;
import com.nori.tc.ui.core.usecase.ValidateTokenUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.ui.domain.auth.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.lenient;

/**
 * Phase 10 시나리오 검증 테스트의 공통 기반 클래스입니다.
 *
 * <p>테스트 전략:</p>
 * <ul>
 *   <li>{@code @WebMvcTest}: Spring MVC 레이어만 로드 (Kafka/Redis/JPA AutoConfiguration 자동 제외)</li>
 *   <li>포트 인터페이스를 {@code @MockitoBean}으로 교체하여 외부 인프라 의존성 제거</li>
 *   <li>{@link DualResponseRegistry}는 {@code @MockitoSpyBean}으로 실제 동작 유지
 *       (traceId 캡처 및 record() 직접 호출을 위함)</li>
 *   <li>UseCase 및 Registry 실제 Bean 사용 → 비즈니스 로직 통합 검증 포함</li>
 * </ul>
 *
 * <p>공통 픽스처:</p>
 * <ul>
 *   <li>{@link #activeUserInfo}: 활성 상태의 테스트 사용자 도메인 객체</li>
 *   <li>{@link #validSession}: 유효한 세션 객체</li>
 *   <li>{@link #principalWithNoPermission}: 권한 없는 UserPrincipal</li>
 *   <li>{@link #principalWithPermission}: 특정 권한 보유 UserPrincipal</li>
 *   <li>{@link #apiPermission}: API 권한 도메인 객체</li>
 * </ul>
 */
@WebMvcTest(controllers = {
        AuthController.class,
        EqpController.class,
        DlqController.class,
        AsyncResultController.class
})
@Import({
        // Controllers: @WebMvcTest는 @SpringBootApplication 기준 패키지(com.nori.tc.apps.uibackend)만
        // 스캔하므로, com.nori.tc.ui.adapters.web.controller 패키지의 컨트롤러들은
        // @Import로 명시해야 Spring MVC 컨텍스트에 등록됩니다.
        AuthController.class,
        EqpController.class,
        DlqController.class,
        AsyncResultController.class,
        UiSecurityConfig.class,
        UiTokenAuthenticationFilter.class,
        UiApiPermissionCache.class,
        LoginUseCase.class,
        ValidateTokenUseCase.class,
        LogoutUseCase.class,
        DualResponseRegistry.class,
        UiCommandIngressService.class,
        UiBackendScenarioTestSupport.TestJacksonConfig.class
})
@EnableConfigurationProperties({
        UiAuthProperties.class,
        UiDualRequestProperties.class
})
@TestPropertySource(properties = {
        // 세션 유효 시간: 기본값 8시간 유지
        "tc.ui.backend.auth.session-ttl-hours=8",
        // Redis 토큰 캐시 TTL: 기본값 300초 유지
        "tc.ui.backend.auth.token-cache-ttl-seconds=300",
        // DualResponse 타임아웃: 테스트에서는 5초로 단축
        "tc.ui.backend.async.dual-request-timeout-ms=5000"
})
abstract class UiBackendScenarioTestSupport {

    // ─────────────────────────────────────────────────────────
    // 테스트 전용 Jackson 2.x ObjectMapper 빈 제공 설정
    // ─────────────────────────────────────────────────────────

    /**
     * 테스트 컨텍스트에 Jackson 2.x ObjectMapper 빈을 명시적으로 제공합니다.
     *
     * <p>Spring Boot 4.x는 Jackson 3.x({@code tools.jackson.databind.ObjectMapper})를 기본으로
     * 사용하므로 {@code com.fasterxml.jackson.databind.ObjectMapper} 빈이 자동 등록되지 않습니다.
     * {@link UiTokenAuthenticationFilter}가 Jackson 2.x ObjectMapper를 생성자 주입으로 요구하므로
     * 테스트 컨텍스트에 명시적으로 제공합니다.</p>
     */
    @Configuration
    static class TestJacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    // ─────────────────────────────────────────────────────────
    // 테스트 픽스처 상수
    // ─────────────────────────────────────────────────────────

    /** 테스트 사용자 PK */
    static final long TEST_USER_PK = 1L;

    /** 테스트 사용자 ID (정규화된 소문자) */
    static final String TEST_USER_ID = "testuser";

    /**
     * 테스트용 64자 Bearer 세션 토큰.
     * 실제 LoginUseCase가 생성하는 alphanumeric 64자 형식을 모방합니다.
     */
    static final String TEST_TOKEN =
            "TestTokenAABBCCDD11223344TestTokenAABBCCDD11223344TestTokenAABBCCDD";

    /** 설비 관리 권한 코드 */
    static final String EQP_MANAGE_PERM = "EQP_MANAGE";

    /** 테스트 설비 ID */
    static final String TEST_EQP_ID = "EQP-TEST-001";

    // ─────────────────────────────────────────────────────────
    // Spring MockMvc
    // ─────────────────────────────────────────────────────────

    @Autowired
    MockMvc mockMvc;

    // ─────────────────────────────────────────────────────────
    // UiApiPermissionCache: @Autowired (loadPermissions() 재호출 용도)
    // ─────────────────────────────────────────────────────────

    @Autowired
    UiApiPermissionCache apiPermissionCache;

    // ─────────────────────────────────────────────────────────
    // DualResponseRegistry: @MockitoSpyBean (실제 동작 + verify/captor 지원)
    // ─────────────────────────────────────────────────────────

    @MockitoSpyBean
    DualResponseRegistry dualResponseRegistry;

    // ─────────────────────────────────────────────────────────
    // DB 포트 MockBean
    // ─────────────────────────────────────────────────────────

    @MockitoBean
    UserPort userPort;

    @MockitoBean
    SessionPort sessionPort;

    @MockitoBean
    PermissionPort permissionPort;

    @MockitoBean
    PasswordVerifierPort passwordVerifierPort;

    @MockitoBean
    UiApiPermissionPort apiPermissionPort;

    // ─────────────────────────────────────────────────────────
    // Redis 포트 MockBean
    // ─────────────────────────────────────────────────────────

    @MockitoBean
    TokenCachePort tokenCachePort;

    @MockitoBean
    AsyncResultStorePort asyncResultStorePort;

    @MockitoBean
    DualResponseRedisPort dualResponseRedisPort;

    @MockitoBean
    GatewayDlqPort gatewayDlqPort;

    @MockitoBean
    BusinessDlqPort businessDlqPort;

    // ─────────────────────────────────────────────────────────
    // Kafka 발행/라우팅 포트 MockBean
    // ─────────────────────────────────────────────────────────

    @MockitoBean
    UiGatewayEventPublishPort gatewayEventPublishPort;

    @MockitoBean
    UiBusinessEventPublishPort businessEventPublishPort;

    @MockitoBean
    UiGatewayEqpRoutePartitionLookupPort routePartitionLookupPort;

    // ─────────────────────────────────────────────────────────
    // 공통 픽스처 초기화
    // ─────────────────────────────────────────────────────────

    /**
     * 각 테스트 전에 기본 API 권한 캐시를 로드합니다.
     *
     * <p>현재 정책은 closed by default 이므로, 테스트에서 호출하는 보호 API를
     * 명시적으로 등록해두고 필요한 시나리오에서 {@link #reloadPermissions(List)}로 재정의합니다.</p>
     */
    @BeforeEach
    void setUpCommonMocks() {
        // ValidateTokenUseCase가 캐시 히트 경로에서도 주기적으로 DB 유효 세션을 재검증하므로,
        // 공통 픽스처에서 기본 토큰(TEST_TOKEN)에 대한 유효 세션을 미리 설정합니다.
        // 각 시나리오에서 만료/폐기 상태를 검증해야 하는 경우 테스트 메서드 내부 stubbing으로 덮어씁니다.
        lenient().when(sessionPort.findValidByToken(TEST_TOKEN)).thenReturn(java.util.Optional.of(validSession()));

        lenient().when(apiPermissionPort.findAllActiveApiPermissions()).thenReturn(List.of(
                apiPermission(EQP_MANAGE_PERM, "/api/eqp", null),
                apiPermission(EQP_MANAGE_PERM, "/api/async", "GET"),
                apiPermission(EQP_MANAGE_PERM, "/api/dlq", null),
                apiPermission("AUTH_ME_PERM", "/auth/me", "GET"),
                apiPermission("AUTH_LOGOUT_PERM", "/auth/logout", "POST")
        ));
        apiPermissionCache.loadPermissions();
    }

    // ─────────────────────────────────────────────────────────
    // 공통 테스트 헬퍼 메서드
    // ─────────────────────────────────────────────────────────

    /**
     * API 권한 캐시를 지정한 권한 목록으로 재로딩합니다.
     *
     * <p>특정 권한 설정이 필요한 시나리오(403 검증 등)에서 호출합니다.
     * {@link UiApiPermissionCache#loadPermissions()}가 {@link UiApiPermissionPort}에서
     * 새로 권한을 로드하므로, 먼저 {@code apiPermissionPort} stubbing을 변경해야 합니다.</p>
     *
     * @param permissions 설정할 API 권한 목록
     */
    void reloadPermissions(final List<TcUiPermission> permissions) {
        lenient().when(apiPermissionPort.findAllActiveApiPermissions()).thenReturn(permissions);
        apiPermissionCache.loadPermissions();
    }

    // ─────────────────────────────────────────────────────────
    // 픽스처 생성 팩토리 메서드
    // ─────────────────────────────────────────────────────────

    /**
     * 활성 상태의 테스트 사용자 도메인 객체를 생성합니다.
     *
     * @param passwordHash BCrypt로 해시된 비밀번호
     * @return 테스트용 TcUserInfo (UserStatus.ACTIVE)
     */
    TcUserInfo activeUserInfo(final String passwordHash) {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcUserInfo(
                TEST_USER_PK,
                "NORI",
                "개발팀",
                "테스트유저",
                TEST_USER_ID,
                TEST_USER_ID,   // user_id_norm = userId.trim().toLowerCase()
                passwordHash,
                "test@nori.com",
                UserStatus.ACTIVE,
                now, now,
                "SYSTEM", "SYSTEM"
        );
    }

    /**
     * 유효한 세션 객체를 생성합니다 (revoked=false, 8시간 후 만료).
     *
     * @return 테스트용 TcUiAuthSession
     */
    TcUiAuthSession validSession() {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcUiAuthSession(
                TEST_TOKEN,
                TEST_USER_PK,
                now,
                now.plusHours(8),
                null,
                false
        );
    }

    /**
     * 권한 코드를 하나도 보유하지 않은 UserPrincipal을 생성합니다.
     *
     * <p>현재 정책은 closed by default 이므로, 권한이 없으면 보호 API 접근이 차단됩니다.</p>
     *
     * @return 권한 없는 UserPrincipal
     */
    UserPrincipal principalWithNoPermission() {
        return new UserPrincipal(TEST_USER_PK, TEST_USER_ID, Set.of());
    }

    /**
     * 지정한 권한 코드를 보유한 UserPrincipal을 생성합니다.
     *
     * @param permCodes 부여할 권한 코드 목록
     * @return 권한 보유 UserPrincipal
     */
    UserPrincipal principalWithPermission(final String... permCodes) {
        return new UserPrincipal(TEST_USER_PK, TEST_USER_ID, Set.of(permCodes));
    }

    /**
     * PREFIX 매칭 방식의 API 권한 도메인 객체를 생성합니다.
     *
     * <p>matchType=PREFIX이므로 requestUri.startsWith(resource)로 매칭합니다.
     * httpMethod=null이면 모든 HTTP 메서드에 적용됩니다.</p>
     *
     * @param permCode   권한 코드 (예: EQP_MANAGE)
     * @param resource   URI 접두사 (예: /api/eqp)
     * @param httpMethod HTTP 메서드 (null이면 전체 메서드 허용)
     * @return 테스트용 TcUiPermission
     */
    TcUiPermission apiPermission(final String permCode, final String resource, final String httpMethod) {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcUiPermission(
                1L,
                permCode,
                permCode + " 권한",
                TcUiPermissionResourceType.API,
                TcUiPermissionMatchType.PREFIX,
                resource,
                httpMethod,
                null,
                true,
                now, now,
                "SYSTEM", "SYSTEM"
        );
    }
}
