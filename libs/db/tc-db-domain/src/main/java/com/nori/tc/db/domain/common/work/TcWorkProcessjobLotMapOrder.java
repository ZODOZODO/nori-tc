package com.nori.tc.db.domain.common.work;

/**
 * tc_work_processjob_lot_map.map_order 컬럼에서 허용하는 정렬/방향 값.
 *
 * <p>
 * [DB 제약 조건]
 * - map_order IS NULL OR map_order IN ('FORWARD', 'REVERSE')
 * </p>
 *
 * <p>
 * [사용 목적]
 * - 도메인 계층에서 허용 값(열거형)을 명시하여
 *   문자열 오타나 허용되지 않는 값 저장을 예방한다.
 * </p>
 */
public enum TcWorkProcessjobLotMapOrder {
    /**
     * 정방향 매핑.
     */
    FORWARD,

    /**
     * 역방향 매핑.
     */
    REVERSE
}
