package com.nori.tc.comm.gateway.context.port;

import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;

import java.util.Objects;

/**
 * 게이트웨이 런타임 hot path에서 사용할 설비 메타 조회 포트입니다.
 *
 * <p>이 포트의 목적은 바인딩/명령 디스패치/런타임 제어 경로에서
 * DB 직접 조회 대신 인메모리 EQP bean(EquipmentContextRegistry)을 단일 조회 기준으로 사용하도록
 * 호출부를 표준화하는 것입니다.</p>
 *
 * <p>반환 타입은 단순 Optional 대신 조회 결과 상태를 함께 제공하여,
 * 호출부가 UNKNOWN_EQUIPMENT / INVALID_PROFILE 같은 운영 의미를 구분해 로그를 남길 수 있도록 설계합니다.</p>
 */
public interface EquipmentRuntimeCatalog {

    /**
     * eqpId 기준으로 런타임 검증에 필요한 설비 메타 정보를 조회합니다.
     *
     * <p>조회 성공 시 {@link LookupStatus#FOUND}와 함께 {@link GatewayEquipmentInfo}를 반환합니다.</p>
     * <p>컨텍스트/프로파일/메타가 누락된 경우에는 상태값으로 원인을 구분합니다.</p>
     *
     * @param eqpId 설비 ID
     * @return 조회 결과(상태 + 메타)
     */
    LookupResult find(String eqpId);

    /**
     * 런타임 메타 조회 결과입니다.
     *
     * @param status 조회 상태
     * @param equipmentInfo 조회 성공 시 설비 메타(실패 시 null)
     */
    record LookupResult(
            LookupStatus status,
            GatewayEquipmentInfo equipmentInfo
    ) {

        /**
         * 레코드 생성 시 필수 상태값을 검증합니다.
         *
         * @param status 조회 상태
         * @param equipmentInfo 설비 메타 정보(FOUND일 때만 필수)
         */
        public LookupResult {
            Objects.requireNonNull(status, "status is null");
            if (status == LookupStatus.FOUND && equipmentInfo == null) {
                throw new IllegalArgumentException("equipmentInfo is required when status=FOUND");
            }
        }

        /**
         * 조회 성공 결과를 생성합니다.
         *
         * @param equipmentInfo 설비 메타
         * @return 조회 성공 결과
         */
        public static LookupResult found(final GatewayEquipmentInfo equipmentInfo) {
            return new LookupResult(LookupStatus.FOUND, Objects.requireNonNull(equipmentInfo, "equipmentInfo is null"));
        }

        /**
         * 조회 실패 결과를 생성합니다.
         *
         * @param status 실패 상태
         * @return 조회 실패 결과
         */
        public static LookupResult miss(final LookupStatus status) {
            if (status == null || status == LookupStatus.FOUND) {
                throw new IllegalArgumentException("miss status must not be null/FOUND");
            }
            return new LookupResult(status, null);
        }

        /**
         * 조회 성공 여부를 반환합니다.
         *
         * @return 성공이면 true
         */
        public boolean found() {
            return status == LookupStatus.FOUND && equipmentInfo != null;
        }
    }

    /**
     * 런타임 메타 조회 상태 코드입니다.
     */
    enum LookupStatus {
        FOUND,
        INVALID_EQP_ID,
        CONTEXT_NOT_FOUND,
        PROFILE_NOT_FOUND,
        EQUIPMENT_INFO_NOT_FOUND
    }
}
