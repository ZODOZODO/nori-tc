package com.nori.tc.db.core.user.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.user.upsert.UpsertTcUserGroup;
import com.nori.tc.db.domain.user.TcUserGroup;

/**
 * tc_user_group CRUD 인터페이스 (기술 중립).
 *
 * <p>
 * 구현 책임:
 * </p>
 * <ul>
 *     <li>JPA 구현: tc-db-jpa-*-schema 모듈이 구현체 제공</li>
 *     <li>MyBatis 구현: tc-db-mybatis-*-schema 모듈이 구현체 제공</li>
 * </ul>
 *
 * <p>
 * 예외 정책(권장):
 * </p>
 * <ul>
 *     <li>중복(유니크 위반 등): DbDuplicateKeyException</li>
 *     <li>DB 접근 실패: DbAccessException</li>
 * </ul>
 */
public interface TcUserGroupStore {

    /**
     * 사용자 그룹 upsert.
     *
     * <p>
     * - groupId가 있으면 해당 PK 기반으로 갱신합니다.
     * - groupId가 없으면 groupCode 유니크 키 기준으로 존재 여부를 확인해 갱신/생성을 수행합니다.
     * </p>
     *
     * @return upsert 후 상태의 TcUserGroup
     */
    TcUserGroup upsert(UpsertTcUserGroup command);

    /**
     * PK 기준 단건 조회.
     */
    Optional<TcUserGroup> findByGroupId(long groupId);

    /**
     * group_code 기준 단건 조회.
     */
    Optional<TcUserGroup> findByGroupCode(String groupCode);

    /**
     * 목록 조회 + 페이징.
     */
    List<TcUserGroup> findAll(PageRequest page);

    /**
     * PK 기준 삭제.
     */
    void deleteByGroupId(long groupId);
}
