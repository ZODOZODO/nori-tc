package com.nori.tc.business.domain.work;

import com.nori.tc.db.domain.work.TcWork;
import com.nori.tc.db.domain.work.TcWorkCarrier;
import com.nori.tc.db.domain.work.TcWorkControlJob;
import com.nori.tc.db.domain.work.TcWorkLot;
import com.nori.tc.db.domain.work.TcWorkProcessJob;

import java.util.List;
import java.util.Objects;

/**
 * work 단위의 전체 컨텍스트 (work + 관련 서브 엔티티).
 *
 * <p>
 * 불변 record로 설계하여 스레드 안전성을 확보합니다.
 * lot, carrier, controlJob, processJob 정보를 하나의 단위로 묶어
 * 메모리 캐시에서 단건 조회로 모든 컨텍스트를 가져올 수 있습니다.
 * </p>
 *
 * <p>
 * controlJob은 nullable입니다.
 * ControlJob을 사용하지 않는 설비도 존재하므로 absent 상태를 null로 표현합니다.
 * controlJob이 null이면 processJobs는 항상 빈 리스트입니다.
 * </p>
 */
public record WorkEntry(
        TcWork work,
        List<TcWorkLot> lots,
        List<TcWorkCarrier> carriers,
        TcWorkControlJob controlJob,
        List<TcWorkProcessJob> processJobs
) {

    /**
     * 정규화된 WorkEntry를 생성합니다.
     *
     * <p>null 컬렉션은 빈 불변 리스트로 정규화합니다.</p>
     */
    public WorkEntry {
        Objects.requireNonNull(work, "work is null");
        lots = lots != null ? List.copyOf(lots) : List.of();
        carriers = carriers != null ? List.copyOf(carriers) : List.of();
        processJobs = processJobs != null ? List.copyOf(processJobs) : List.of();
    }

    /**
     * 이 work에 해당 lotId가 포함되어 있는지 확인합니다.
     *
     * @param lotId 조회할 lot ID
     * @return lot 포함 여부
     */
    public boolean hasLotId(final String lotId) {
        if (lotId == null) {
            return false;
        }
        for (TcWorkLot lot : lots) {
            if (lotId.equals(lot.lotId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 이 work에 해당 carrierId가 포함되어 있는지 확인합니다.
     *
     * @param carrierId 조회할 carrier ID
     * @return carrier 포함 여부
     */
    public boolean hasCarrierId(final String carrierId) {
        if (carrierId == null) {
            return false;
        }
        for (TcWorkCarrier carrier : carriers) {
            if (carrierId.equals(carrier.carrierId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 이 work의 carrier 중 해당 portId를 가진 carrier가 있는지 확인합니다.
     *
     * @param portId 조회할 port ID
     * @return carrier.portId 포함 여부
     */
    public boolean hasPortId(final String portId) {
        if (portId == null) {
            return false;
        }
        for (TcWorkCarrier carrier : carriers) {
            if (portId.equals(carrier.portId())) {
                return true;
            }
        }
        return false;
    }
}
