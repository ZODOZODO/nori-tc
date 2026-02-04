package com.nori.tc.db.starter.mysql.jpa;

import com.nori.tc.db.jpa.common.JpaCommonSchemaMarker;
import com.nori.tc.db.jpa.site.JpaSiteSchemaMarker;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * MySQL + JPA Starter AutoConfiguration (FIX)
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
 *   → 설정은 표준 spring.datasource.* / spring.jpa.* 로 제공합니다.
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
public class TcDbMysqlJpaAutoConfiguration {

    /**
     * Starter 배타 락 Bean (FIX)
     *
     * 모든 tc-db-*-*-starter가 동일한 Bean 이름을 사용해야 합니다.
     * - 그러면 starter를 2개 이상 의존 시 Bean name collision로 부팅이 즉시 실패합니다.
     *
     * 타입은 중요하지 않으므로 Object/String으로 단순화합니다.
     */
    @Bean(name = "tcDbStarterExclusiveLock")
    public Object tcDbStarterExclusiveLock() {
        return "tc-db-mysql-jpa-starter";
    }
}
