package com.nori.tc.db.core.exception;

/**
 * 조회/갱신/삭제 대상이 존재하지 않을 때 사용합니다.
 *
 * - Optional 반환으로도 표현 가능하지만,
 *   "반드시 존재해야 한다"는 문맥에서는 예외가 더 명확할 때가 있습니다.
 */
public class DbEntityNotFoundException extends DbAccessException {

    
    /**
     * DB Core 계층 구성 요소를 초기화합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     */
    public DbEntityNotFoundException(String message) {
        super(message);
    }

    
    /**
     * DB Core 계층 구성 요소를 초기화합니다.
     *
     * <p>포트/유스케이스 규약과 저장소 추상화를 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     * @param cause DB Core 계층 처리에 사용하는 입력 값
     */
    public DbEntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
