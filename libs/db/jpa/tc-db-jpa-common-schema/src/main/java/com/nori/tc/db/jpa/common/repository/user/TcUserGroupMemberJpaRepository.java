package com.nori.tc.db.jpa.common.repository.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.user.TcUserGroupMemberEntity;

/**
 * tc_user_group_member Spring Data JPA Repository.
 */
public interface TcUserGroupMemberJpaRepository extends JpaRepository<TcUserGroupMemberEntity, Long> {

    
    /**
     * DB JPA 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB JPA 계층 처리에 사용하는 입력 값
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUserGroupMemberEntity> findByUserPkAndGroupId(long userPk, long groupId);

    
    /**
     * DB JPA 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>엔티티 생명주기 콜백과 컬럼 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB JPA 계층 처리에 사용하는 입력 값
     * @param groupId DB JPA 계층 처리에 사용하는 입력 값
     */
    void deleteByUserPkAndGroupId(long userPk, long groupId);
}
