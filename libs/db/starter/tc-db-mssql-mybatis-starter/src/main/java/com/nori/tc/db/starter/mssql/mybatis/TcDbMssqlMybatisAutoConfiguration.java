package com.nori.tc.db.starter.mssql.mybatis;

import com.nori.tc.db.mybatis.common.MybatisCommonSchemaMarker;
import com.nori.tc.db.mybatis.site.MybatisSiteSchemaMarker;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * MSSQL + MyBatis Starter AutoConfiguration (FIX)
 *
 * 하는 일
 * 1) MapperScan 범위를 common + site schema로 고정
 * 2) Store 구현체(@Repository/@Component) 컴포넌트 스캔 범위를 common + site schema로 고정
 * 3) Starter 배타 락 Bean 등록(중복 starter 의존 시 fail-fast)
 *
 * 하지 않는 일
 * - DataSource를 직접 생성하지 않습니다. (spring.datasource.* 표준 설정 사용)
 * - SqlSessionFactory를 직접 생성하지 않습니다.
 *   → mybatis-spring-boot-starter의 기본 AutoConfiguration에 위임합니다.
 *
 * mapper XML 위치
 * - XML 파일들은 tc-db-mybatis-common-schema 모듈에 위치합니다.
 * - XML 위치 지정은 보통 mybatis.mapper-locations 프로퍼티로 처리하는 것이 가장 깔끔합니다.
 */
@AutoConfiguration
@MapperScan(basePackageClasses = {
        MybatisCommonSchemaMarker.class,
        MybatisSiteSchemaMarker.class
})
@ComponentScan(basePackageClasses = {
        MybatisCommonSchemaMarker.class,
        MybatisSiteSchemaMarker.class
})
public class TcDbMssqlMybatisAutoConfiguration {

    @Bean(name = "tcDbStarterExclusiveLock")
    public Object tcDbStarterExclusiveLock() {
        return "tc-db-mssql-mybatis-starter";
    }
}
