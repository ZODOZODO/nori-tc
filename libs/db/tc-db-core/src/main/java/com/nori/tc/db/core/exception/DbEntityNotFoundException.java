package com.nori.tc.db.core.exception;

/**
 * 조회/갱신/삭제 대상이 존재하지 않을 때 사용합니다.
 *
 * - Optional 반환으로도 표현 가능하지만,
 *   "반드시 존재해야 한다"는 문맥에서는 예외가 더 명확할 때가 있습니다.
 */
public class DbEntityNotFoundException extends DbAccessException {

    public DbEntityNotFoundException(String message) {
        super(message);
    }

    public DbEntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
