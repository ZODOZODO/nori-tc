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
    api(project(":libs:comm:tc-comm-gateway-core"))

    /*
     * Redis DLQ/Quarantine 엔트리 직렬화 계약입니다.
     * - RedisDlqEntry, RedisQuarantineEntry 클래스가 tc-db-domain에 위치합니다.
     * - tc-ui-redis-adapter가 동일 클래스를 역직렬화할 수 있도록 공유 도메인에서 참조합니다.
     */
    implementation(project(":libs:db:tc-db-domain"))

    /*
     * Redis 공통 CRUD 레이어(TcRedisCrudRepository)를 제공합니다.
     */
    implementation(project(":libs:db:starter:tc-db-redis-starter"))

    // Spring 컴포넌트 컴파일 의존성 (@Component/@Service)
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
}

tasks.test {
    useJUnitPlatform()
}
