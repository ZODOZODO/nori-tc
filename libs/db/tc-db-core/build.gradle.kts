/**
 * tc-db-core
 *
 * 역할
 * - "App이 직접 의존하는" DB 접근 포트(Interface) 계층
 *   예) TcEqpStore, TcModelStore 등
 *
 * 핵심 설계
 * - App은 JPA/MyBatis/벤더(Postgres/Oracle/...)를 몰라도 되지만,
 *   "DB를 통해 CRUD 한다"는 개념(포트)은 알아야 하므로 core를 의존한다.
 *
 * 중요 포인트 (이번 컴파일 에러의 원인과 직결)
 * - tc-db-core는 반환 타입/enum 등에서 tc-db-domain을 사용한다.
 * - App이 tc-db-core만 의존해도 domain 타입까지 같이 보이도록 하려면,
 *   tc-db-domain은 implementation 이 아니라 api 로 노출되어야 한다.
 *
 *   ✅ 즉, 아래처럼 api(project(":libs:db:tc-db-domain")) 이어야
 *      App/Test에서 com.nori.tc.db.domain.* import가 정상 동작한다.
 */
plugins {
    `java-library`
}

group = "com.nori.tc"
version = "0.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    /**
     * ✅ domain을 "api"로 노출
     *
     * - core의 public API(메서드 시그니처/반환 타입/enum)에 domain 타입이 등장한다.
     * - 따라서 core를 사용하는 모듈(App/Test)은 domain 타입도 컴파일 클래스패스에서
     *   볼 수 있어야 한다.
     */
    api(project(":libs:db:tc-db-domain"))

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
