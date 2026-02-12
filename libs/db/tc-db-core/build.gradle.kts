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
