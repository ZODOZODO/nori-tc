package com.nori.tc.db.mybatis.common.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.common.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;

/**
 * tc_eqp Mapper (FIX)
 *
 * - eqp_id가 PK이므로 insert/update를 분리 제공한다.
 * - upsert(벤더별 문법)는 starter/adapter에서 흡수하는 쪽이 안전하다.
 * - [2024-XX-XX FIX] 메모리 페이징 이슈 해결을 위해 findAll에 DB 페이징 파라미터 추가
 */
public interface TcEqpMapper {

    int insert(@Param("e") TcEqp eqp);

    int update(@Param("e") TcEqp eqp);

    Optional<TcEqp> findByEqpId(@Param("eqpId") String eqpId);

    /**
     * 페이징 검색 (FIX)
     * - 기존: 메모리로 다 가져와서 자름 (OOM 위험)
     * - 변경: DB에서 잘라오도록 offset/limit 추가
     *
     * @param protocolType 프로토콜 타입 (nullable)
     * @param enabled 사용 여부 (nullable)
     * @param offset 건너뛸 행 수
     * @param limit 가져올 행 수
     */
    List<TcEqp> findAll(
            @Param("protocolType") ProtocolType protocolType,
            @Param("enabled") Boolean enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int deleteByEqpId(@Param("eqpId") String eqpId);
}