plugins {
    `java-library`

    // BOM 적용 목적:
    // - 루트 build.gradle.kts에서 dependencyManagement(BOM import)를 걸고,
    // - 이 플러그인이 적용된 모듈은 BOM 버전 관리를 받는다.
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
    /*
     * domain
     * - Mapper 파라미터/결과 DTO/enum을 직접 사용
     */
    implementation(project(":libs:db:tc-db-domain"))

    /*
     * core
     * - Store(Port) 인터페이스 + Command/Criteria/Exception 타입 사용
     */
    implementation(project(":libs:db:tc-db-core"))

    /*
     * MyBatis core 타입(@Param 등)만 컴파일에 필요
     * - ✅ 버전은 libs.versions.toml에서 관리: libs.mybatis.core (= org.mybatis:mybatis)
     * - 런타임 의존성은 starter(app)가 mybatis-spring-boot-starter로 제공
     */
    compileOnly(libs.mybatis.core)

    /*
     * Spring compile-only
     * - Store 구현체가 @Repository / @Transactional / DataAccessException 등을 사용
     * - ✅ 좌표는 catalog(alias) 사용, 버전은 Spring Boot BOM이 관리
     */
    compileOnly(libs.spring.context)
    compileOnly(libs.spring.tx)

    /*
     * Test
     * - 테스트에서 Mapper/Store를 직접 컴파일/호출 가능하도록 최소 의존성만 둔다.
     */
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mybatis.core)
}

tasks.test {
    useJUnitPlatform()
}
