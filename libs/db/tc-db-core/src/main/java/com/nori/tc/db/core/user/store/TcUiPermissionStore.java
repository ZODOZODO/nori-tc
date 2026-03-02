package com.nori.tc.db.core.user.store;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.user.upsert.UpsertTcUiPermission;
import com.nori.tc.db.domain.user.TcUiPermission;

/**
 * tc_ui_permission CRUD 인터페이스 (기술 중립).
 *
 * <p>
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 * </p>
 *
 * <p>
 * 예외 정책(권장):
 * - 중복(유니크 위반 등): DbDuplicateKeyException
 * - DB 접근 실패: DbAccessException
 * </p>
 */
public interface TcUiPermissionStore {

    /**
     * 권한 upsert.
     *
     * <p>
     * - perm_id가 있으면 해당 PK 기반으로 갱신한다.
     * - perm_id가 없으면 perm_code UNIQUE 키 기준으로 생성하고,
     *   생성 후에는 perm_code로 재조회하여 최종 상태를 반환한다.
     * </p>
     *
     * @return upsert 후 상태의 TcUiPermission
     */
    TcUiPermission upsert(UpsertTcUiPermission command);

    /**
     * PK 기반 단건 조회.
     */
    Optional<TcUiPermission> findByPermId(long permId);

    /**
     * 권한 코드(perm_code) 기반 단건 조회.
     */
    Optional<TcUiPermission> findByPermCode(String permCode);

    /**
     * 목록 조회 + 페이징.
     */
    List<TcUiPermission> findAll(PageRequest page);

    /**
     * perm_id 목록 기준 활성 권한 전체 조회 (IN 절 + isActive=true, 페이징 없음).
     *
     * <p>
     * 권한 조회 시 permId 목록으로 활성 권한 코드를 한 번에 조회할 때 사용한다.
     * </p>
     *
     * @param permIds 조회할 perm_id 컬렉션
     * @return isActive=true인 권한 목록
     */
    List<TcUiPermission> findAllActiveByPermIdIn(Collection<Long> permIds);

    /**
     * 삭제.
     *
     * <p>
     * FK(tc_user_group_permission.perm_id)가 존재하므로,
     * 운영 정책상 삭제 금지/소프트 삭제는 상위 계층에서 통제한다.
     * </p>
     */
    void deleteByPermId(long permId);
}
