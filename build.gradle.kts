/*
 * 루트 build.gradle.kts
 *
 * 적용 내용
 * - io.spring.dependency-management 플러그인을 사용하는 모든 모듈에서
 *   spring-boot-dependencies BOM을 import 하도록 설정합니다.
 *   이를 통해 Spring Boot starter 버전 누락 문제를 방지합니다.
 *
 * 추가 정리
 * - java-library 플러그인은 java 플러그인을 포함하므로
 *   중복 설정을 피하고 java 블록 기준으로 공통 설정을 관리합니다.
 */

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "com.nori.tc"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    /**
     * dependency-management 플러그인 적용 모듈에 Spring Boot BOM을 강제 import 합니다.
     * - org.springframework.boot:spring-boot-starter-* 의 버전 해석을 안정화합니다.
     *
     * 주의
     * - 이 블록은 io.spring.dependency-management 플러그인이 적용된 모듈에서만 동작합니다.
     * - 해당 플러그인을 적용하지 않으면 BOM 기반 버전 관리가 적용되지 않습니다.
     */
    pluginManager.withPlugin("io.spring.dependency-management") {
        extensions.configure(DependencyManagementExtension::class.java) {
            imports {
                // spring-boot 플러그인 버전과 동일한 BOM 버전을 사용합니다.
                mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
            }
        }
    }

    // 공통 로깅 의존성
    pluginManager.withPlugin("java") {
        dependencies {
            add("compileOnly", libs.slf4j.api)
        }
    }

    // Spring Boot 앱에는 log-starter 자동 주입
    pluginManager.withPlugin("org.springframework.boot") {
        pluginManager.withPlugin("java") {
            dependencies {
                add("implementation", project(":libs:log:starter:tc-log-starter"))
            }
        }
    }

    /**
     * Java 공통 설정
     *
     * - Toolchain: Java 21
     * - 인코딩: UTF-8
     * - 컴파일러 인자: -parameters
     * - 테스트: JUnit Platform
     */
    pluginManager.withPlugin("java") {
        extensions.configure(org.gradle.api.plugins.JavaPluginExtension::class.java) {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType(JavaCompile::class.java).configureEach {
            options.encoding = "UTF-8"
            // 중복 추가를 방지하기 위해 기존 인자 존재 여부를 확인합니다.
            if (!options.compilerArgs.contains("-parameters")) {
                options.compilerArgs.add("-parameters")
            }
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }
    }
}
