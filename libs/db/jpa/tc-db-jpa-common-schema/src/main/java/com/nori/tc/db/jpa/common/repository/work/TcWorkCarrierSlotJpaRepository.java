package com.nori.tc.db.jpa.common.repository.work;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nori.tc.db.jpa.common.entity.work.TcWorkCarrierSlotEntity;

/**
 * tc_work_carrier_slot Repository
 */
public interface TcWorkCarrierSlotJpaRepository extends JpaRepository<TcWorkCarrierSlotEntity, Long> {

    Optional<TcWorkCarrierSlotEntity> findByWorkCarrierKeyAndSlotNo(Long workCarrierKey, Integer slotNo);

    List<TcWorkCarrierSlotEntity> findByWorkCarrierKey(Long workCarrierKey);
}
