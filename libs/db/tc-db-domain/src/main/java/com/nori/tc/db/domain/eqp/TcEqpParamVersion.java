package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_param_version 테이블 1행에 대응하는 순수 DTO입니다.
 *
 * PK:
 * - eqp_param_version_key (IDENTITY)
 *
 * FK:
 * - eqp_key -> tc_eqp.eqp_key ON DELETE CASCADE
 *
 * Unique:
 * - (eqp_key, param_version)
 *
 * 설계 메모:
 * - versionDescription은 특정 버전 전체에 대한 설명입니다.
 * - tc_eqp_param.description은 개별 파라미터 설명이므로 별도 테이블로 분리합니다.
 */
public record TcEqpParamVersion(
        long eqpParamVersionKey,
        long eqpKey,
        String paramVersion,
        String versionDescription,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
