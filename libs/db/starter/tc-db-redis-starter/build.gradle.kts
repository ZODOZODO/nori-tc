/*
 * tc-db-redis-starter
 *
 * 역할
 * - Redis 접근 기술을 앱에서 "한 줄"로 조립할 수 있도록 Starter 형태로 제공
 *
 * 목표
 * - Redis 관련 의존성을 starter 모듈로 집중시켜
 *   앱은 implementation(project(":libs:db:starter:tc-db-redis-starter")) 한 줄만 추가하도록 한다.
 *
 * 포함
 * - spring-boot-starter-data-redis
 * - starter 배타 락(fail-fast): 동일 Bean 이름 등록
 *
 * 설정 방식
 * - Redis 설정은 Spring 표준 프로퍼티(spring.data.redis.*) 사용
 * - 필요한 값(host/port/password 등)은 app의 application.yaml에서 주입
 */

plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Redis 접근을 위한 Spring Data Redis Starter
    implementation(libs.spring.boot.starter.data.redis)


    testImplementation(libs.spring.boot.starter.test)
}

tasks.test {
    useJUnitPlatform()
}
