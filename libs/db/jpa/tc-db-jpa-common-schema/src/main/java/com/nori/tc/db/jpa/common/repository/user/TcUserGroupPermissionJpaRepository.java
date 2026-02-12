package com.nori.tc.db.jpa.common.repository.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.user.TcUserGroupPermissionEntity;

/**
 * tc_user_group_permission Repository.
 *
 * <p>
 * - Unique Key는 (group_id, perm_id)이며, 해당 조합으로 단건 조회를 제공합니다.
 * - group_id 기준 목록 조회는 store 레이어에서 페이징으로 사용됩니다.
 * </p>
 */
public interface TcUserGroupPermissionJpaRepository extends JpaRepository<TcUserGroupPermissionEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     * @param permId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUserGroupPermissionEntity> findByGroupIdAndPermId(Long groupId, Long permId);

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회/처리 결과 목록
     */
    List<TcUserGroupPermissionEntity> findByGroupId(Long groupId);
}
