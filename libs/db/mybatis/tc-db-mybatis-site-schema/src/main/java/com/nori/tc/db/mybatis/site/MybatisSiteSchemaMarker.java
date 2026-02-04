package com.nori.tc.db.mybatis.site;

/**
 * MyBatis Site Schema Marker (FIX)
 *
 * 용도
 * - starter에서 MapperScan 또는 mapperLocations 구성 시 기준점으로 사용한다.
 * - site-schema는 현재 비어있지만, 항상 연결을 고정하기 위해 marker를 둔다.
 */
public final class MybatisSiteSchemaMarker {

    private MybatisSiteSchemaMarker() {
        // 인스턴스화 방지
    }
}
