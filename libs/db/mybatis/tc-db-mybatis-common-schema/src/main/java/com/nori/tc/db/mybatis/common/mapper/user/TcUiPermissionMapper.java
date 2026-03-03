package com.nori.tc.db.mybatis.common.mapper.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.user.TcUiPermission;

/**
 * tc_ui_permission Mapper (FIX)
 *
 * <p>
 * - perm_id는 IDENTITY PK이므로 insert/update를 분리 제공한다.
 * - perm_code는 UNIQUE이므로 서비스/스토어 계층에서 upsert 규칙을 정의한다.
 * - 목록 조회는 DB 페이징을 사용한다.
 * </p>
 */
public interface TcUiPermissionMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permission DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("p") TcUiPermission permission);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permission DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("p") TcUiPermission permission);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUiPermission> findByPermId(@Param("permId") long permId);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permCode DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUiPermission> findByPermCode(@Param("permCode") String permCode);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcUiPermission> findAll(
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * perm_id 목록 기준 활성 권한 전체 조회 (IN 절 + is_active=true, 페이징 없음).
     *
     * @param permIds 조회할 perm_id 컬렉션
     * @return is_active=true인 권한 목록
     */
    List<TcUiPermission> findAllActiveByPermIdIn(@Param("permIds") Collection<Long> permIds);

    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param permId DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByPermId(@Param("permId") long permId);
}
