package com.nori.tc.db.jpa.site;

/**
 * JPA Site Schema Marker (FIX)
 *
 * 용도
 * - starter에서 @EntityScan / @EnableJpaRepositories의 basePackageClasses 기준으로 사용합니다.
 * - site-schema는 현재 비어있지만, "항상 연결"을 고정하기 위해 marker를 둡니다.
 *
 * 장점
 * - 문자열 패키지 지정 대신 컴파일 타임 안전 확보
 * - site 모듈이 비어 있어도 스캔 기준점이 명확해져 조립이 안정적
 */
public final class JpaSiteSchemaMarker {

    private JpaSiteSchemaMarker() {
        // 인스턴스화 방지용
    }
}
