package com.nori.tc.db.mybatis.common.mapper.work;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import com.nori.tc.db.domain.work.TcWorkCarrierSlot;

/**
 * tc_work_carrier_slot Mapper (FIX)
 *
 * - Unique Key: (work_carrier_key, slot_no)
 * - carrier_slot_key는 IDENTITY이므로 insert 후 재조회 방식 사용
 */
public interface TcWorkCarrierSlotMapper {

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param slot DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int insert(@Param("s") TcWorkCarrierSlot slot);

    
    /**
     * DB MyBatis 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param slot DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int update(@Param("s") TcWorkCarrierSlot slot);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workCarrierKey 대상 키 값
     * @param slotNo DB MyBatis 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkCarrierSlot> findByWorkCarrierKeySlotNo(@Param("workCarrierKey") long workCarrierKey, @Param("slotNo") int slotNo);

    
    /**
     * DB MyBatis 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workCarrierKey 대상 키 값
     * @param offset 페이징/조회 범위 조건
     * @param limit 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcWorkCarrierSlot> findAllByWorkCarrierKey(@Param("workCarrierKey") long workCarrierKey, @Param("offset") int offset, @Param("limit") int limit);

    
    /**
     * DB MyBatis 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     * @param workCarrierKey 대상 키 값
     * @param slotNo DB MyBatis 계층 처리에 사용하는 입력 값
     * @return DB MyBatis 계층 처리 결과
     */
    int deleteByWorkCarrierKeySlotNo(@Param("workCarrierKey") long workCarrierKey, @Param("slotNo") int slotNo);
}
