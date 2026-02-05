package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.ProtocolType;

/**
 * tc_eqp 테이블 1행에 대응하는 순수 DTO.
 *
 * [DB 스키마 요약]
 * - eqp_key        : bigint PK (IDENTITY)
 * - eqp_id         : varchar(64) UNIQUE (비즈니스 키)
 * - comm_interface : varchar(16) (HSMS, SOCKET)
 * - eqp_ip         : varchar(45)
 * - eqp_port       : int (1~65535)
 * - model_key      : bigint FK -> tc_model.model_key
 * - enabled        : boolean (default true)
 * - created_at     : timestamptz
 * - updated_at     : timestamptz
 * - created_by     : varchar(50) (default SYSTEM)
 * - updated_by     : varchar(50) (default SYSTEM)
 */
public record TcEqp(
        Long eqpKey,
        String eqpId,
        ProtocolType commInterface,
        String eqpIp,
        int eqpPort,
        long modelKey,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
