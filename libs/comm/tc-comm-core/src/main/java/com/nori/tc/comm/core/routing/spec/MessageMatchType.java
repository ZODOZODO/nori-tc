package com.nori.tc.comm.core.routing.spec;

/**
 * 메시지명 매칭 방식
 *
 * 운영에서 룰은 자주 바뀌지만, 무분별한 표현력(스크립트)은 리스크가 큽니다.
 * 따라서 선언형으로 제한된 매칭 타입만 제공합니다.
 */
public enum MessageMatchType {
    EXACT,
    PREFIX,
    CONTAINS,
    REGEX
}
