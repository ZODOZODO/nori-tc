package com.nori.tc.db.mybatis.common;

/**
 * MyBatis Common Schema Marker (FIX)
 *
 * 용도
 * - starter에서 MapperScan 또는 mapperLocations 구성 시 기준점으로 사용한다.
 * - 문자열 기반 패키지 지정 대신 "컴파일 타임 안전"을 확보한다.
 *
 * 예)
 * - @MapperScan(basePackageClasses = MybatisCommonSchemaMarker.class)
 * - mapperLocations = classpath*:mybatis/common/*.xml
 */
public final class MybatisCommonSchemaMarker {

    private MybatisCommonSchemaMarker() {
        // 인스턴스화 방지
    }
}
