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

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param session 통신 채널/세션 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("s") TcUiAuthSession session);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param session 통신 채널/세션 정보
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("s") TcUiAuthSession session);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param token DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcUiAuthSession> findByToken(@Param("token") String token);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param userPk DB MyBatis 계층 처리에 사용하는 입력 값
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcUiAuthSession> findAllByUserPk(
            @Param("userPk") long userPk,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param token DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByToken(@Param("token") String token);
}
