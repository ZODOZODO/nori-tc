package com.nori.tc.db.mybatis.site;

/**
 * MyBatis Site Schema Marker (FIX)
 *
 * 용도
 * - starter에서 MapperScan 또는 mapperLocations 구성 시 기준점으로 사용한다.
 * - site-schema는 현재 비어있지만, 항상 연결을 고정하기 위해 marker를 둔다.
 */
public final class MybatisSiteSchemaMarker {

    
    /**
     * DB MyBatis 계층 구성 요소를 초기화합니다.
     *
     * <p>매퍼 SQL 파라미터/결과 매핑 규칙을 기준으로 처리합니다.</p>
     */
    private MybatisSiteSchemaMarker() {
        // 인스턴스화 방지
    }
}
