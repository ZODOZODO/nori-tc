package com.nori.tc.db.core.work.store;

import java.util.List;
import java.util.Optional;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.upsert.UpsertTcWorkLot;
import com.nori.tc.db.domain.work.TcWorkLot;

/**
 * tc_work_lot CRUD 인터페이스.
 *
 * <p>
 * - Unique(work_key, lot_id)를 기준으로 upsert를 수행한다.
 * - updated_at 은 DB에서 자동 갱신되므로 클라이언트가 직접 제어하지 않는다.
 * </p>
 */
public interface TcWorkLotStore {

    
    /**
     * DB Core 계층 데이터의 저장/갱신을 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 정보
     * @return DB Core 계층 처리 결과
     */
    TcWorkLot upsert(UpsertTcWorkLot command);

    
    /**
     * DB Core 계층에서 필요한 데이터를 조회합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workKey 대상 키 값
     * @param lotId DB Core 계층 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    Optional<TcWorkLot> findByWorkKeyAndLotId(long workKey, String lotId);

    /**
     * 특정 작업(work_key)에 연결된 LOT 목록 조회.
     * - 페이징은 반드시 DB 레벨에서 처리해야 한다.
     */
    List<TcWorkLot> findAllByWorkKey(long workKey, PageRequest page);

    
    /**
     * DB Core 계층 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param workLotKey 대상 키 값
     */
    void deleteByWorkLotKey(long workLotKey);
}
