/*
 * 루트 build.gradle.kts (FIX)
 *
 * 핵심 Fix
 * - io.spring.dependency-management 플러그인을 적용한 모든 모듈에
 *   spring-boot-dependencies BOM을 import 해서
 *   Spring Boot starter들의 "버전 공백(:)" 문제를 제거한다.
 *
 * 추가 Fix(권장)
 * - java-library는 내부적으로 java 플러그인을 포함하므로,
 *   java / java-library 블록을 둘 다 두면 설정이 중복 적용될 수 있다.
 *   -> java 블록만 두고 java-library 블록은 제거한다.
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
     * FIX: dependency-management 적용 모듈에 Spring Boot BOM 강제 import
     * - 이걸 해야 org.springframework.boot:spring-boot-starter-xxx 들이 버전 없이도 resolve 됩니다.
     *
     * 주의
     * - 이 블록은 io.spring.dependency-management 플러그인을 "명시적으로" 적용한 모듈에만 동작합니다.
     * - 만약 어떤 모듈이 스타터를 쓰는데도 dependency-management를 적용하지 않으면,
     *   (또는 spring-boot 플러그인만 적용하고 이 플러그인을 적용하지 않으면)
     *   버전 해석이 달라질 수 있으니 모듈별 플러그인 적용 정책을 통일하세요.
     */
    pluginManager.withPlugin("io.spring.dependency-management") {
        extensions.configure(DependencyManagementExtension::class.java) {
            imports {
                // spring-boot 플러그인 버전(= libs.versions.springBoot)과 BOM 버전을 동일하게 고정
                mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
            }
        }
    }

    /**
     * Java 공통
     *
     * - java-library도 java를 포함하므로 여기 한 번만 설정한다.
     * - UTF-8 / -parameters / JUnit Platform 통일
     */
    pluginManager.withPlugin("java") {
        extensions.configure(org.gradle.api.plugins.JavaPluginExtension::class.java) {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType(JavaCompile::class.java).configureEach {
            options.encoding = "UTF-8"
            // 중복 추가 방지를 위해 존재 여부 체크 후 추가
            if (!options.compilerArgs.contains("-parameters")) {
                options.compilerArgs.add("-parameters")
            }
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }
    }
}
