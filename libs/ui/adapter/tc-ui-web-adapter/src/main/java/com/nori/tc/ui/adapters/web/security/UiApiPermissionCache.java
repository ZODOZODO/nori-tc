package com.nori.tc.ui.adapters.web.security;

import com.nori.tc.db.domain.common.user.TcUiPermissionMatchType;
import com.nori.tc.db.domain.user.TcUiPermission;
import com.nori.tc.ui.core.port.db.UiApiPermissionPort;
import com.nori.tc.ui.domain.auth.UserPrincipal;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 활성 API 권한 목록을 메모리에 캐시하는 컴포넌트입니다.
 *
 * <p>역할:</p>
 * <p>애플리케이션 기동 시 {@link UiApiPermissionPort}를 통해 tc_ui_permission 테이블의
 * 활성 API 권한을 전체 로드하여 메모리에 보관합니다.
 * {@link UiSecurityConfig}의 커스텀 {@code AuthorizationManager}가 매 HTTP 요청마다
 * 이 캐시를 참조하여 URL 인가 판단을 수행합니다.</p>
 *
 * <p>인가 판단 규칙:</p>
 * <ul>
 *   <li>요청 URI에 해당하는 API 권한이 없는 경우 → 인증된 사용자에게 허용</li>
 *   <li>요청 URI에 매칭되는 API 권한이 있는 경우 → 사용자가 해당 permCode 보유 시 허용</li>
 *   <li>matchType=PREFIX → requestUri.startsWith(permission.resource)</li>
 *   <li>matchType=EXACT  → requestUri.equals(permission.resource)</li>
 *   <li>httpMethod=null  → 모든 HTTP 메서드 허용</li>
 *   <li>httpMethod 지정  → 요청 메서드와 대소문자 무관 비교</li>
 * </ul>
 *
 * <p>캐시 갱신:</p>
 * <p>권한 데이터는 기동 시 1회 로드됩니다.
 * 권한 변경 사항은 애플리케이션 재시작 후 반영됩니다.
 * (동적 갱신이 필요한 경우 별도 refresh API 구현을 검토하십시오.)</p>
 */
@Component
public class UiApiPermissionCache {

    private static final Logger log = LoggerFactory.getLogger(UiApiPermissionCache.class);

    private final UiApiPermissionPort apiPermissionPort;

    /**
     * volatile: 기동 후 단 1회 write가 발생하고 이후 read만 발생하므로
     * synchronized 없이 volatile만으로 가시성을 보장합니다.
     */
    private volatile List<TcUiPermission> cachedPermissions = List.of();

    /**
     * 필수 의존성을 초기화합니다.
     *
     * @param apiPermissionPort API 권한 전체 조회 포트
     */
    public UiApiPermissionCache(final UiApiPermissionPort apiPermissionPort) {
        this.apiPermissionPort = Objects.requireNonNull(apiPermissionPort, "apiPermissionPort is null");
    }

    /**
     * 기동 시 API 권한 목록을 로드합니다.
     *
     * <p>DB 조회 실패 시에도 애플리케이션 기동을 중단하지 않고
     * 빈 캐시로 시작합니다 (권한 없으면 인증된 사용자에게 허용되므로
     * 보안 위험이 없음). 대신 ERROR 로그를 남겨 운영자에게 알립니다.</p>
     */
    @PostConstruct
    public void loadPermissions() {
        try {
            final List<TcUiPermission> loaded = apiPermissionPort.findAllActiveApiPermissions();
            this.cachedPermissions = loaded;
            log.info("API 권한 캐시 로드 완료. 총 {}개 API 권한 활성.", loaded.size());
        } catch (Exception e) {
            // DB 조회 실패는 기동을 중단하지 않음 - 빈 캐시로 운영
            log.error("API 권한 캐시 로드 실패. 빈 캐시로 시작합니다. "
                    + "인가 판단이 비정상 동작할 수 있으니 DB 상태를 확인하십시오.", e);
        }
    }

    /**
     * 요청 URI와 HTTP 메서드에 대해 사용자의 접근 권한을 판단합니다.
     *
     * <p>매칭되는 API 권한이 없으면 인증된 사용자에게 허용합니다 (open by default for authenticated).
     * 매칭되는 권한이 있으면 사용자의 permissionCodes 중 하나가 포함되어야 허용합니다.</p>
     *
     * @param principal  인증된 사용자 정보
     * @param httpMethod 요청 HTTP 메서드 (예: GET, POST)
     * @param requestUri 요청 URI (예: /api/eqp/EQP001)
     * @return 접근 허용이면 true, 거부면 false
     */
    public boolean isAuthorized(final UserPrincipal principal, final String httpMethod, final String requestUri) {
        final List<TcUiPermission> matching = cachedPermissions.stream()
                .filter(p -> matchUri(p, requestUri))
                .filter(p -> matchMethod(p, httpMethod))
                .toList();

        if (matching.isEmpty()) {
            // 해당 URI에 설정된 API 권한 없음 → 인증된 사용자에게 허용
            log.trace("API 권한 설정 없음 - 허용. uri={}, method={}, userPk={}",
                    requestUri, httpMethod, principal.userPk());
            return true;
        }

        // 매칭된 권한 중 사용자가 보유한 permCode가 있으면 허용
        final boolean allowed = matching.stream()
                .anyMatch(p -> principal.hasPermission(p.permCode()));

        if (!allowed) {
            log.warn("API 권한 부족. uri={}, method={}, userPk={}, requiredPermCodes={}",
                    requestUri, httpMethod, principal.userPk(),
                    matching.stream().map(TcUiPermission::permCode).toList());
        } else {
            log.trace("API 권한 확인 완료 - 허용. uri={}, method={}, userPk={}",
                    requestUri, httpMethod, principal.userPk());
        }

        return allowed;
    }

    // -------------------------------------------------------------------------
    // 내부 매칭 로직
    // -------------------------------------------------------------------------

    /**
     * 권한의 matchType에 따라 요청 URI가 매칭되는지 확인합니다.
     *
     * @param perm       권한 항목
     * @param requestUri 요청 URI
     * @return 매칭되면 true
     */
    private boolean matchUri(final TcUiPermission perm, final String requestUri) {
        if (perm.resource() == null || perm.resource().isBlank()) {
            return false;
        }
        if (perm.matchType() == TcUiPermissionMatchType.PREFIX) {
            return requestUri.startsWith(perm.resource());
        }
        // EXACT 또는 기타: 완전 일치
        return requestUri.equals(perm.resource());
    }

    /**
     * 권한의 httpMethod 설정에 따라 요청 메서드가 매칭되는지 확인합니다.
     *
     * <p>httpMethod가 null 또는 공백이면 모든 메서드를 허용합니다.</p>
     *
     * @param perm       권한 항목
     * @param httpMethod 요청 HTTP 메서드
     * @return 매칭되면 true
     */
    private boolean matchMethod(final TcUiPermission perm, final String httpMethod) {
        if (perm.httpMethod() == null || perm.httpMethod().isBlank()) {
            // httpMethod 미설정 → 모든 HTTP 메서드 허용
            return true;
        }
        return perm.httpMethod().equalsIgnoreCase(httpMethod);
    }
}
