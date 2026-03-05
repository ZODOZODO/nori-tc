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

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param userPk DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUserInfo> findByUserPk(long userPk);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param userIdNorm DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUserInfo> findByUserIdNorm(String userIdNorm);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param email DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUserInfo> findByEmail(String email);

    /**
     * 사용자 전체 목록을 페이지 단위로 조회합니다.
     *
     * <p>
     * 화면 기반 사용자 관리 목록 API에서 사용하는 기본 조회 메서드입니다.
     * 조건 검색이 없는 순수 전체 목록 조회이며, 반드시 offset/limit 페이징을 적용합니다.
     * </p>
     *
     * @param page 페이징 조건(없으면 구현체 기본 페이지 적용 권장)
     * @return 조회된 사용자 목록
     */
    List<TcUserInfo> findAll(PageRequest page);

    /**
     * 회사/부서 기준 목록 조회 + 페이징
     */
    List<TcUserInfo> findAllByCompanyDepartment(String company, String department, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param userPk DB Core 계층 처리에 사용하는 입력 값
     */
    void deleteByUserPk(long userPk);
}
