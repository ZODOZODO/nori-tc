package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.model.ProtocolType;

/**
 * tc_eqp 테이블 1행에 대응하는 순수 DTO입니다.
 *
 * <p>역할:</p>
 * <p>- DB 저장소 계층(MyBatis/JPA)에서 조회한 {@code tc_eqp} 행을 도메인 계층으로 전달하는 읽기 전용 모델입니다.</p>
 * <p>- 설비의 식별 정보, 통신 인터페이스, 공통 통신 모드, Gateway 라우팅 파티션 정보를 단일 객체로 제공합니다.</p>
 *
 * <p>중요 설계 규칙:</p>
 * <p>- {@code commMode}는 과거 {@code tc_eqp_hsms.connection_mode}, {@code tc_eqp_socket.connection_mode}에
 *   분산되어 있던 값을 {@code tc_eqp.comm_mode}로 통합한 값입니다.</p>
 * <p>- {@code routePartition}은 Gateway 대상 Kafka 토픽({@code tc.eqp.commands}, {@code tc.ui.events.gateway})
 *   라우팅의 단일 기준(SSOT)입니다.</p>
 * <p>- {@code routePartition}은 U3 시점 기준 nullable이며, 후속 단계(U14/U17)에서 배정/검증 규칙이 강화됩니다.</p>
 *
 * <p>[DB 스키마 요약]</p>
 * <p>- eqp_key         : bigint PK (IDENTITY)</p>
 * <p>- eqp_id          : varchar(64) UNIQUE (비즈니스 키)</p>
 * <p>- comm_interface  : varchar(16) (HSMS, SOCKET)</p>
 * <p>- comm_mode       : varchar(10) (ACTIVE, PASSIVE)</p>
 * <p>- route_partition : int nullable (Gateway 대상 토픽 고정 라우팅 partition)</p>
 * <p>- eqp_ip          : varchar(45)</p>
 * <p>- eqp_port        : int (1~65535)</p>
 * <p>- model_version_key       : bigint FK -> tc_model_version.model_version_key</p>
 * <p>- enabled         : boolean (default true)</p>
 * <p>- created_at      : timestamptz</p>
 * <p>- updated_at      : timestamptz</p>
 * <p>- created_by      : varchar(50) (default SYSTEM)</p>
 * <p>- updated_by      : varchar(50) (default SYSTEM)</p>
 */
public record TcEqp(
        Long eqpKey,
        String eqpId,
        ProtocolType commInterface,
        String commMode,
        Integer routePartition,
        String eqpIp,
        int eqpPort,
        long modelVersionKey,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
