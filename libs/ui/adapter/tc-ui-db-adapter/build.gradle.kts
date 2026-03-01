/*
 * tc-ui-db-adapter
 *
 * 역할
 * - tc-ui-core의 DB Port 인터페이스(UserPort, SessionPort, PermissionPort)를 JPA로 구현합니다.
 * - 기존 tc-db-jpa-common-schema의 Entity를 재사용하며 신규 Entity 없음.
 *
 * 주요 구성 (Phase 3에서 구현)
 * Repository (기존 Entity 사용):
 *   - TcUserInfoRepository          : userIdNorm 조회
 *   - TcUiAuthSessionRepository     : token + revoked + expiresAt 조건 조회
 *   - TcUserGroupMemberRepository   : userPk로 groupId 목록
 *   - TcUserGroupPermissionRepository: groupId 목록으로 permId 목록
 *   - TcUiPermissionRepository      : permId + isActive 조회
 *   - TcEqpRepository               : eqpId → route_partition 조회 (U13)
 * Port 구현체:
 *   - JpaUserPort, JpaSessionPort, JpaPermissionPort
 *   - UiEqpRoutePartitionDbAdapter  : U13 (BusinessEqpRoutePartitionDbAdapter 동일 패턴)
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
    /*
     * DB 어댑터는 코어 Port를 구현하므로 코어를 공개 의존성으로 노출합니다.
     */
    api(project(":libs:ui:tc-ui-core"))

    /*
     * 기존 common-schema JPA Entity를 재사용합니다.
     * - TcUserInfoEntity, TcUiAuthSessionEntity, TcUserGroupMemberEntity 등
     * - tc-db-core: Repository/Store 인터페이스 계약
     */
    implementation(project(":libs:db:tc-db-core"))
    implementation(project(":libs:db:jpa:tc-db-jpa-common-schema"))

    /*
     * Spring 컴포넌트/설정 바인딩 컴파일 의존성
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
