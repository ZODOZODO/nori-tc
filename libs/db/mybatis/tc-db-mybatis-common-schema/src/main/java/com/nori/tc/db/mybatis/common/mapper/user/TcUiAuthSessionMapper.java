package com.nori.tc.db.mybatis.common.mapper.user;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.user.TcUiAuthSession;

/**
 * tc_ui_auth_session Mapper (FIX)
 *
 * <p>
 * 특징:
 * - PK는 token(문자열) 하나로 구성된다.
 * - user_pk 인덱스를 사용한 사용자별 목록 조회를 제공한다.
 * </p>
 */
public interface TcUiAuthSessionMapper {

    int insert(@Param("s") TcUiAuthSession session);

    int update(@Param("s") TcUiAuthSession session);

    Optional<TcUiAuthSession> findByToken(@Param("token") String token);

    List<TcUiAuthSession> findAllByUserPk(
            @Param("userPk") long userPk,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByToken(@Param("token") String token);
}
