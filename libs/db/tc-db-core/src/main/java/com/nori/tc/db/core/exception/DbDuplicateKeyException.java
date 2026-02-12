package com.nori.tc.db.core.exception;

/**
 * 유니크 제약/PK 중복 등으로 인해 저장이 실패할 때 사용합니다.
 *
 * 예:
 * - tc_model (model_name, model_version) UNIQUE 위반
 * - tc_eqp eqp_id PK 중복
 */
public class DbDuplicateKeyException extends DbAccessException {

    
    /**
     * DB Core 계층 구성 요소를 초기화합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     */
    public DbDuplicateKeyException(String message) {
        super(message);
    }

    
    /**
     * DB Core 계층 구성 요소를 초기화합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     * @param cause DB Core 계층 처리에 사용하는 입력 값
     */
    public DbDuplicateKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
