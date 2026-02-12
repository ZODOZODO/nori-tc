package com.nori.tc.db.core.exception;

/**
 * DB 접근 계층에서 발생하는 "일반적인" 예외의 최상위.
 *
 * - 구현체(JPA/MyBatis)가 던지는 예외를 그대로 외부로 노출하지 않기 위해 사용합니다.
 * - Adapter 계층에서 예외를 변환하여 이 예외(또는 하위 예외)로 던지도록 설계합니다.
 */
public class DbAccessException extends RuntimeException {

    
    /**
     * DB Core 계층 구성 요소를 초기화합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     */
    public DbAccessException(String message) {
        super(message);
    }

    
    /**
     * DB Core 계층 구성 요소를 초기화합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     * @param cause DB Core 계층 처리에 사용하는 입력 값
     */
    public DbAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
