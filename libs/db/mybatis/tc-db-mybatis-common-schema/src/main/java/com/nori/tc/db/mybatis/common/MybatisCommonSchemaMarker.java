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

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     */
    private MybatisCommonSchemaMarker() {
        // 인스턴스화 방지
    }
}
