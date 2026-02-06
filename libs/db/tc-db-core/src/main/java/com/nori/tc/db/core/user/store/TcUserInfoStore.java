package com.nori.tc.db.core.user.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.user.upsert.UpsertTcUserInfo;
import com.nori.tc.db.domain.user.TcUserInfo;

/**
 * tc_user_info CRUD 인터페이스 (기술 중립)
 *
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 *
 * 주의 사항:
 * - user_pk는 FK(tc_ui_auth_session, tc_user_group_member)가 참조한다.
 * - tc_user_group_member는 ON DELETE CASCADE 이므로 삭제 시 연쇄 삭제가 발생할 수 있다.
 */
public interface TcUserInfoStore {

    /**
     * 사용자 정보 upsert.
     *
     * <p>
     * - user_pk가 있으면 PK 기반 갱신을 우선합니다.
     * - user_pk가 없으면 user_id_norm 유니크 키 기준으로 갱신/생성을 수행합니다.
     * </p>
     */
    TcUserInfo upsert(UpsertTcUserInfo command);

    Optional<TcUserInfo> findByUserPk(long userPk);

    Optional<TcUserInfo> findByUserIdNorm(String userIdNorm);

    Optional<TcUserInfo> findByEmail(String email);

    /**
     * 회사/부서 기준 목록 조회 + 페이징
     */
    List<TcUserInfo> findAllByCompanyDepartment(String company, String department, PageRequest page);

    void deleteByUserPk(long userPk);
}
