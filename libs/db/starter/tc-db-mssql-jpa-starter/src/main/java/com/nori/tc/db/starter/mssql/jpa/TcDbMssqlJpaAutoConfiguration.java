package com.nori.tc.db.starter.mssql.jpa;

import com.nori.tc.db.jpa.common.JpaCommonSchemaMarker;
import com.nori.tc.db.jpa.site.JpaSiteSchemaMarker;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * MSSQL + JPA Starter AutoConfiguration (FIX)
 *
 * 하는 일
 * 1) JPA Entity 스캔 범위를 common + site schema로 고정
 * 2) Spring Data JPA Repository 스캔 범위를 common + site schema로 고정
 * 3) Store 구현체(@Repository) 컴포넌트 스캔 범위를 common + site schema로 고정
 * 4) Starter 배타 락 Bean 등록(중복 starter 의존 시 fail-fast)
 *
 * 하지 않는 일(중요)
 * - DataSource / EntityManagerFactory / TransactionManager를 직접 생성하지 않습니다.
 *   → Spring Boot 기본 AutoConfiguration에 위임합니다.
 *   → 즉, 설정은 표준 spring.datasource.* / spring.jpa.* 로 제공합니다.
 */
@AutoConfiguration
@EntityScan(basePackageClasses = {
        JpaCommonSchemaMarker.class,
        JpaSiteSchemaMarker.class
})
@EnableJpaRepositories(basePackageClasses = {
        JpaCommonSchemaMarker.class,
        JpaSiteSchemaMarker.class
})
@ComponentScan(basePackageClasses = {
        JpaCommonSchemaMarker.class,
        JpaSiteSchemaMarker.class
})
public class TcDbMssqlJpaAutoConfiguration {

    /**
     * Starter 배타 락 Bean (FIX)
     *
     * Bean 이름을 "모든 tc-db-*-*-starter에서 동일하게" 사용해야 합니다.
     * - 예: tc-db-postgres-jpa-starter도 같은 이름을 사용
     * - 그러면 2개 이상 starter가 함께 들어오면 Bean name collision로 부팅 실패
     */
    @Bean(name = "tcDbStarterExclusiveLock")
    public TcDbStarterExclusiveLock tcDbStarterExclusiveLock() {
        return new TcDbStarterExclusiveLock("tc-db-mssql-jpa-starter");
    }
}
