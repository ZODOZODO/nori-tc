package com.nori.tc.db.core.jar.upsert;

/**
 * tc_jar_gateway upsert 입력(Command)
 *
 * 상세 규칙:
 * - eqpKey는 필수이며 tc_eqp.eqp_key를 참조합니다.
 * - jarFileName/jarFile은 필수입니다.
 * - createdBy/updatedBy가 null 또는 blank면 구현체에서 SYSTEM으로 보정합니다.
 */
public record UpsertTcJarGateway(
        Long eqpKey,
        String jarFileName,
        byte[] jarFile,
        String createdBy,
        String updatedBy
) {
}
