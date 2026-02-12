package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkCarrierSlot;
import com.nori.tc.db.domain.work.TcWorkCarrierSlot;

/**
 * tc_work_carrier_slot CRUD 인터페이스.
 *
 * - (work_carrier_key, slot_no) 유니크 키 기반의 upsert 및 조회를 제공합니다.
 * - 대량 조회는 PageRequest(offset/limit)를 받아 페이징합니다.
 */
public interface TcWorkCarrierSlotStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcWorkCarrierSlot upsert(UpsertTcWorkCarrierSlot command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workCarrierKey 대상 키 값
     * @param slotNo DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkCarrierSlot> findByWorkCarrierKeySlotNo(long workCarrierKey, int slotNo);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workCarrierKey 대상 키 값
     * @param page 페이징/조회 범위 조건
     * @return 조회/처리 결과 목록
     */
    List<TcWorkCarrierSlot> findAllByWorkCarrierKey(long workCarrierKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workCarrierKey 대상 키 값
     * @param slotNo DB Core 계층 처리에 사용하는 입력 값
     */
    void deleteByWorkCarrierKeySlotNo(long workCarrierKey, int slotNo);
}
