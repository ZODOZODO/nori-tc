package com.nori.tc.db.core.eqp.upsert;

/**
 * tc_eqp_param_version upsert 입력(Command)입니다.
 *
 * <p>버전 설명은 설비별 버전 메타데이터이므로
 * (eqp_key, param_version) unique key 기준으로 upsert 합니다.</p>
 */
public record UpsertTcEqpParamVersion(
        long eqpKey,
        String paramVersion,
        String versionDescription,
        String createdBy,
        String updatedBy
) {

    private static final int PARAM_VERSION_MAX_LENGTH = 100;
    private static final int VERSION_DESCRIPTION_MAX_LENGTH = 2000;
    private static final int AUDIT_USER_MAX_LENGTH = 50;

    public UpsertTcEqpParamVersion {
        if (eqpKey <= 0) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }
        if (paramVersion == null || paramVersion.isBlank()) {
            throw new IllegalArgumentException("paramVersion must not be null/blank");
        }
        if (paramVersion.length() > PARAM_VERSION_MAX_LENGTH) {
            throw new IllegalArgumentException("paramVersion must be <= " + PARAM_VERSION_MAX_LENGTH + " characters");
        }
        if (versionDescription != null && versionDescription.length() > VERSION_DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("versionDescription must be <= " + VERSION_DESCRIPTION_MAX_LENGTH + " characters");
        }
        if (createdBy != null && createdBy.length() > AUDIT_USER_MAX_LENGTH) {
            throw new IllegalArgumentException("createdBy must be <= " + AUDIT_USER_MAX_LENGTH + " characters");
        }
        if (updatedBy != null && updatedBy.length() > AUDIT_USER_MAX_LENGTH) {
            throw new IllegalArgumentException("updatedBy must be <= " + AUDIT_USER_MAX_LENGTH + " characters");
        }
    }
}
