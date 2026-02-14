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
     * Gateway 코어 + SOCKET 플러그인 포트 계약 의존성입니다.
     * - 런타임 매니저가 코어 포트/도메인 계약을 구현할 때 필요합니다.
     */
    api(project(":libs:comm:tc-comm-gateway-core"))
    api(project(":libs:comm:tc-comm-socket"))

    /*
     * UI JARFILE 이벤트 확장 포인트를 구현하기 위한 의존성입니다.
     * - GatewayUiJarfileTaskProcessor / GatewayUiTaskResult / ErrorCode 사용
     */
    implementation(project(":libs:comm:adapter:tc-comm-gateway-kafka-adapter"))
    implementation(project(":libs:messaging:starter:tc-messaging-kafka-starter"))

    /*
     * 플러그인 JAR 조회를 위한 DB 포트/도메인 의존성입니다.
     * - tc_eqp 조회: TcEqpStore
     * - tc_jar_gateway 조회: TcJarGatewayStore
     */
    implementation(project(":libs:db:tc-db-core"))
    implementation(project(":libs:db:tc-db-domain"))

    /*
     * Spring 컴포넌트/설정 바인딩/라이프사이클 어노테이션 컴파일 의존성입니다.
     */
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    /*
     * 단위 테스트 의존성입니다.
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
