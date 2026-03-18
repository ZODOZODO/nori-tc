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
     * SocketTypeHandler / SocketTypeDecodeResult / SocketTypeEncodeResult / SocketFrame 등
     * gateway action SPI 계약을 직접 참조합니다.
     */
    implementation("com.nori.tc:nori-tc-gateway-action:0.0.1-SNAPSHOT")

    implementation(libs.netty.all)

    // Spring 컴포넌트 컴파일 의존성 (@Component/@Service)
    compileOnly(libs.spring.boot)
    compileOnly(libs.spring.context)
    compileOnly(libs.jakarta.annotation.api)

    /*
     * 테스트 의존성
     * - GatewayNettyBootstrap 공유 listener / 아웃바운드 연결 동작 검증
     * - final 클래스(GatewayChannelHandler) mocking 을 위해 inline mock maker 사용
     */
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter:5.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation(libs.spring.context)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
