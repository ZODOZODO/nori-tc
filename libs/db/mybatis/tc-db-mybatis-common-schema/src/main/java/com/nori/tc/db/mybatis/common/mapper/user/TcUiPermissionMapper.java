package com.nori.tc.db.mybatis.common.mapper.user;

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

    int insert(@Param("p") TcUiPermission permission);

    int update(@Param("p") TcUiPermission permission);

    Optional<TcUiPermission> findByPermId(@Param("permId") long permId);

    Optional<TcUiPermission> findByPermCode(@Param("permCode") String permCode);

    List<TcUiPermission> findAll(
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByPermId(@Param("permId") long permId);
}
