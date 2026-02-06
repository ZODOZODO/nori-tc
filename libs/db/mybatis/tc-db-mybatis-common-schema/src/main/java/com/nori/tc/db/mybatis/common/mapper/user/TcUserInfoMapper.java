package com.nori.tc.db.mybatis.common.mapper.user;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.user.TcUserInfo;

/**
 * tc_user_info Mapper (FIX)
 *
 * - user_id_norm/email은 UNIQUE 키이므로 insert/update를 분리 제공한다.
 * - DB 벤더별 upsert 문법을 직접 사용하지 않고, Store에서 update-first 전략을 사용한다.
 */
public interface TcUserInfoMapper {

    int insert(@Param("u") TcUserInfo user);

    int updateByUserPk(@Param("u") TcUserInfo user);

    int updateByUserIdNorm(@Param("u") TcUserInfo user);

    Optional<TcUserInfo> findByUserPk(@Param("userPk") long userPk);

    Optional<TcUserInfo> findByUserIdNorm(@Param("userIdNorm") String userIdNorm);

    Optional<TcUserInfo> findByEmail(@Param("email") String email);

    List<TcUserInfo> findAllByCompanyDepartment(
            @Param("company") String company,
            @Param("department") String department,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByUserPk(@Param("userPk") long userPk);
}
