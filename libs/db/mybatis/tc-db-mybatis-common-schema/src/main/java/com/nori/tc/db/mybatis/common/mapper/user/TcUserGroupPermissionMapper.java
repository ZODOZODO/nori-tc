package com.nori.tc.db.mybatis.common.mapper.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.user.TcUserGroupPermission;

/**
 * tc_user_group_permission Mapper (FIX)
 *
 * - Unique Key: (group_id, perm_id)
 * - ugp_key는 IDENTITY이므로 insert 후 재조회 방식 사용
 */
public interface TcUserGroupPermissionMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permission DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("p") TcUserGroupPermission permission);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permission DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("p") TcUserGroupPermission permission);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     * @param permId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUserGroupPermission> findByGroupIdPermId(@Param("groupId") long groupId, @Param("permId") long permId);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcUserGroupPermission> findAllByGroupId(@Param("groupId") long groupId, @Param("offset") int offset, @Param("limit") int limit);

    
    /**
     * group_id 목록 기준 전체 조회 (IN 절, 페이징 없음).
     *
     * @param groupIds 조회할 group_id 컬렉션
     * @return 해당 그룹들의 권한 목록
     */
    List<TcUserGroupPermission> findAllByGroupIdIn(@Param("groupIds") Collection<Long> groupIds);

    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB MyBatis 계층 처리에 사용하는 입력 값
     * @param permId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByGroupIdPermId(@Param("groupId") long groupId, @Param("permId") long permId);
}
