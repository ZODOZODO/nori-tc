package com.nori.tc.business.core.work;

import com.nori.tc.db.domain.work.TcWork;
import com.nori.tc.db.domain.work.TcWorkCarrier;
import com.nori.tc.db.domain.work.TcWorkControlJob;
import com.nori.tc.db.domain.work.TcWorkLot;
import com.nori.tc.db.domain.work.TcWorkProcessJob;

import java.util.List;

/**
 * Work 인메모리 캐시 변경 포트입니다.
 *
 * <p>
 * DB에 work create/update/delete 이후 메모리 캐시를 동기화하기 위해 이 포트를 사용합니다.
 * DB를 source of truth로 하며, DB 반영 후 메모리 갱신을 수행합니다.
 * 갱신 실패 시 {@link #evictEqp(String)}로 해당 eqpId 캐시를 무효화하면
 * 다음 접근 시 DB에서 lazy reload됩니다.
 * </p>
 */
public interface BusinessWorkMutationPort {

    /**
     * work와 관련 서브 엔티티를 캐시에 동기화합니다.
     *
     * <p>DB 반영 후 호출하며, 해당 workId 기준으로 캐시 항목을 교체합니다.</p>
     *
     * @param eqpId       설비 ID
     * @param work        최신 TcWork
     * @param lots        최신 TcWorkLot 목록 (없으면 빈 리스트)
     * @param carriers    최신 TcWorkCarrier 목록 (없으면 빈 리스트)
     * @param controlJob  최신 TcWorkControlJob (없으면 null)
     * @param processJobs 최신 TcWorkProcessJob 목록 (없으면 빈 리스트)
     */
    void syncWork(
            String eqpId,
            TcWork work,
            List<TcWorkLot> lots,
            List<TcWorkCarrier> carriers,
            TcWorkControlJob controlJob,
            List<TcWorkProcessJob> processJobs
    );

    /**
     * 특정 workKey에 해당하는 work를 캐시에서 제거합니다.
     *
     * <p>DB에서 work를 삭제한 후 호출합니다.</p>
     *
     * @param eqpId   설비 ID
     * @param workKey 제거할 work DB PK
     */
    void evictWork(String eqpId, long workKey);

    /**
     * eqpId 전체 캐시를 무효화합니다.
     *
     * <p>
     * Kafka rebalance, 오류 복구, 강제 재로드 등의 상황에서 사용합니다.
     * 무효화 이후 첫 접근 시 DB에서 lazy reload됩니다.
     * </p>
     *
     * @param eqpId 무효화할 설비 ID
     */
    void evictEqp(String eqpId);
}
