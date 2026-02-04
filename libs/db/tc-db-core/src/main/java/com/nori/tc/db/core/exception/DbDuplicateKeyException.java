package com.nori.tc.db.core.exception;

/**
 * 유니크 제약/PK 중복 등으로 인해 저장이 실패할 때 사용합니다.
 *
 * 예:
 * - tc_model (model_name, model_version) UNIQUE 위반
 * - tc_eqp eqp_id PK 중복
 */
public class DbDuplicateKeyException extends DbAccessException {

    public DbDuplicateKeyException(String message) {
        super(message);
    }

    public DbDuplicateKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
