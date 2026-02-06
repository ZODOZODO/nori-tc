package com.nori.tc.db.core.user.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.user.upsert.UpsertTcUserGroupMember;
import com.nori.tc.db.domain.user.TcUserGroupMember;

/**
 * tc_user_group_member CRUD 인터페이스 (기술 중립).
 *
 * <p>
 * 구현 책임:
 * - JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공
 * - MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공
 * </p>
 */
public interface TcUserGroupMemberStore {

    /**
     * 사용자-그룹 관계 upsert.
     *
     * <p>
     * - ugmKey가 있으면 PK 기준 갱신을 시도한다.
     * - ugmKey가 없으면 (user_pk, group_id) 유니크 키 기준으로
     *   존재 여부를 확인한 뒤 갱신/생성을 수행한다.
     * </p>
     *
     * @return upsert 후 상태의 TcUserGroupMember
     */
    TcUserGroupMember upsert(UpsertTcUserGroupMember command);

    /**
     * PK(ugm_key)로 단건 조회.
     */
    Optional<TcUserGroupMember> findByUgmKey(long ugmKey);

    /**
     * 유니크 키(user_pk, group_id)로 단건 조회.
     */
    Optional<TcUserGroupMember> findByUserPkAndGroupId(long userPk, long groupId);

    /**
     * user_pk 기준 목록 조회.
     *
     * <p>
     * 페이징은 반드시 DB 레벨에서 처리해야 한다.
     * </p>
     */
    List<TcUserGroupMember> findAllByUserPk(long userPk, PageRequest page);

    /**
     * group_id 기준 목록 조회.
     *
     * <p>
     * 페이징은 반드시 DB 레벨에서 처리해야 한다.
     * </p>
     */
    List<TcUserGroupMember> findAllByGroupId(long groupId, PageRequest page);

    /**
     * PK(ugm_key) 기준 삭제.
     */
    void deleteByUgmKey(long ugmKey);

    /**
     * 유니크 키(user_pk, group_id) 기준 삭제.
     */
    void deleteByUserPkAndGroupId(long userPk, long groupId);
}
