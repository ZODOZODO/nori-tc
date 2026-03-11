package com.nori.tc.db.core.eqp.upsert;

import com.nori.tc.db.domain.common.model.ProtocolType;

/**
 * tc_eqp upsert 입력(Command)입니다.
 *
 * <p>역할:</p>
 * <p>- {@code tc_eqp} 테이블의 생성/수정 입력값을 저장소 계층으로 전달합니다.</p>
 * <p>- {@code eqp_id}가 UNIQUE 키이므로 생성/수정을 분리하지 않고 upsert 입력 모델로 사용합니다.</p>
 *
 * <p>입력 규칙:</p>
 * <p>- {@code commInterface}는 DB {@code comm_interface} 컬럼(SECS/SOCKET)을 의미합니다.</p>
 * <p>- {@code commMode}는 DB {@code comm_mode} 컬럼(ACTIVE/PASSIVE)을 의미합니다.</p>
 * <p>- {@code isDev}는 DB {@code is_dev} 컬럼이며 개발 장비 여부를 의미합니다.</p>
 * <p>- {@code routePartition}은 Gateway 대상 토픽 고정 라우팅 partition이며 U3 시점 기준 nullable 허용입니다.</p>
 * <p>- {@code routePartition}가 null인 경우는 아직 라우팅 partition이 배정되지 않은 초기/전환 상태를 의미합니다.</p>
 * <p>- created_at/updated_at은 DB(또는 구현체)가 관리하는 것을 권장합니다.</p>
 * <p>- createdBy/updatedBy는 null이면 DB default(SYSTEM) 또는 구현체 기본값으로 대체될 수 있습니다.</p>
 *
 * <p>설계 배경:</p>
 * <p>- 기존 하위 테이블(tc_eqp_hsms/tc_eqp_socket)의 connection_mode를 {@code tc_eqp.comm_mode}로 통합했습니다.</p>
 * <p>- Gateway 고정 라우팅 설계에 맞춰 {@code routePartition}를 tc_eqp 단일 진실 소스로 둡니다.</p>
 */
public record UpsertTcEqp(
        String eqpId,
        ProtocolType commInterface,
        String commMode,
        boolean isDev,
        Integer routePartition,
        String eqpIp,
        int eqpPort,
        long modelVersionKey,
        boolean enabled,
        String createdBy,
        String updatedBy
) {
}
