package com.nori.tc.ui.adapters.redis.session;

import com.nori.tc.ui.domain.auth.UserPrincipal;

import java.io.Serializable;
import java.util.Set;

/**
 * Redis 저장용 UI 세션 캐시 엔트리입니다.
 *
 * <p>역할:</p>
 * <p>{@link UserPrincipal}은 record 타입으로 {@link Serializable}을 구현하지 않습니다.
 * Redis에 JDK 직렬화 방식으로 저장하기 위해 동일한 필드를 담는 어댑터 전용 래퍼 클래스입니다.</p>
 *
 * <p>키 형식: {@code tc:ui:backend:session:{token}}</p>
 * <p>TTL: {@code tc.ui.backend.auth.token-cache-ttl-seconds} 설정값을 따릅니다.</p>
 *
 * <p>직렬화 호환성:</p>
 * <p>필드 추가·삭제 시 {@code serialVersionUID}를 갱신해야 합니다.
 * 기존 캐시 항목이 남아있으면 역직렬화 오류가 발생할 수 있으므로,
 * 변경 후 Redis 캐시 키를 flush 하거나 TTL 만료를 기다려야 합니다.</p>
 */
public class RedisUiSessionEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 사용자 PK (tc_user_info.user_pk) */
    private long userPk;

    /** 사용자 로그인 ID (로그/감사 추적용) */
    private String userId;

    /** 사용자 권한 코드 집합 (tc_ui_permission.perm_code) */
    private Set<String> permissionCodes;

    /**
     * Redis 직렬화 프레임워크용 기본 생성자입니다.
     */
    protected RedisUiSessionEntry() {
    }

    /**
     * {@link UserPrincipal} 데이터를 기반으로 Redis 저장 엔트리를 생성합니다.
     *
     * @param userPk          사용자 PK
     * @param userId          사용자 로그인 ID
     * @param permissionCodes 권한 코드 집합
     */
    public RedisUiSessionEntry(
            final long userPk,
            final String userId,
            final Set<String> permissionCodes
    ) {
        this.userPk = userPk;
        this.userId = userId;
        // 저장 시 방어적 복사 — 원본 Set 변조 방지
        this.permissionCodes = permissionCodes != null ? Set.copyOf(permissionCodes) : Set.of();
    }

    /**
     * {@link UserPrincipal}을 Redis 저장용 엔트리로 변환합니다.
     *
     * @param principal 변환할 UserPrincipal
     * @return Redis 저장용 엔트리
     */
    public static RedisUiSessionEntry from(final UserPrincipal principal) {
        return new RedisUiSessionEntry(
                principal.userPk(),
                principal.userId(),
                principal.permissionCodes()
        );
    }

    /**
     * Redis에서 읽은 엔트리를 도메인 객체 {@link UserPrincipal}로 변환합니다.
     *
     * @return 복원된 UserPrincipal
     */
    public UserPrincipal toPrincipal() {
        return new UserPrincipal(userPk, userId, permissionCodes);
    }

    public long getUserPk() {
        return userPk;
    }

    public String getUserId() {
        return userId;
    }

    public Set<String> getPermissionCodes() {
        return permissionCodes;
    }
}
