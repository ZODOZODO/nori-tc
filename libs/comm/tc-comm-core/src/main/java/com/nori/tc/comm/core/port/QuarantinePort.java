package com.nori.tc.comm.core.port;

import com.nori.tc.comm.core.eqp.EquipmentId;

/**
 * 설비 격리(Quarantine) Port
 *
 * 목적
 * - 폭주/비정상 입력/스크립트 오류 등으로 특정 설비가 전체 시스템에 영향을 주지 않도록 격리합니다.
 * - 격리 구현(채널 autoRead off / 채널 close / DB 기록 등)은 앱에서 수행합니다.
 */
public interface QuarantinePort {

    void quarantine(EquipmentId equipmentId, String reasonCode, String reasonDetail) throws Exception;
}
