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

    int insert(@Param("s") TcWorkCarrierSlot slot);

    int update(@Param("s") TcWorkCarrierSlot slot);

    Optional<TcWorkCarrierSlot> findByWorkCarrierKeySlotNo(@Param("workCarrierKey") long workCarrierKey, @Param("slotNo") int slotNo);

    List<TcWorkCarrierSlot> findAllByWorkCarrierKey(@Param("workCarrierKey") long workCarrierKey, @Param("offset") int offset, @Param("limit") int limit);

    int deleteByWorkCarrierKeySlotNo(@Param("workCarrierKey") long workCarrierKey, @Param("slotNo") int slotNo);
}
