/*
 * tc-db-domain (FIX)
 *
 * 역할
 * - DB 모듈의 Layer 0: "순수 데이터"만 담는다.
 * - Spring, JPA, MyBatis, JDBC Driver 같은 기술 의존성을 절대 두지 않는다.
 *
 * 포함 대상
 * - 테이블/레코드에 대응하는 순수 DTO(불변 권장)
 * - Enum(프로토콜/상태 등)
 *
 * 제외 대상
 * - Repository/Mapper/Entity/AutoConfiguration/Transaction 등 기술 코드
 */

plugins {
    `java-library`
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    // 루트 build.gradle.kts에서 toolchain(Java 21)이 적용되지만,
    // 모듈 단독 실행/분리 시에도 명확히 하기 위해 명시합니다.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // 의도적으로 비워둡니다.
    // domain은 어떤 persistence 기술에도 의존하지 않는 순수 계층이어야 합니다.
}

tasks.test {
    useJUnitPlatform()
}
