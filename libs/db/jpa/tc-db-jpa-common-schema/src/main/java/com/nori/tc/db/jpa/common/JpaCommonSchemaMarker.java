package com.nori.tc.db.jpa.common;

/**
 * JPA Common Schema Marker (FIX)
 *
 * 용도
 * - starter에서 @EntityScan / @EnableJpaRepositories의 basePackageClasses 기준으로 사용합니다.
 * - 패키지 경로를 문자열로 쓰는 것보다 "컴파일 타임 안전"합니다.
 *
 * 예)
 *   @EntityScan(basePackageClasses = JpaCommonSchemaMarker.class)
 *   @EnableJpaRepositories(basePackageClasses = JpaCommonSchemaMarker.class)
 */
public final class JpaCommonSchemaMarker {
    private JpaCommonSchemaMarker() {
        // 인스턴스화 방지용
    }
}
