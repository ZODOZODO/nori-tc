/*
 * 猷⑦듃 build.gradle.kts (FIX)
 *
 * ?듭떖 Fix
 * - io.spring.dependency-management ?뚮윭洹몄씤???곸슜??紐⑤뱺 紐⑤뱢?? *   spring-boot-dependencies BOM??import ?댁꽌
 *   Spring Boot starter?ㅼ쓽 "踰꾩쟾 怨듬갚(:)" 臾몄젣瑜??쒓굅?쒕떎.
 *
 * 異붽? Fix(沅뚯옣)
 * - java-library???대??곸쑝濡?java ?뚮윭洹몄씤???ы븿?섎?濡?
 *   java / java-library 釉붾줉???????먮㈃ ?ㅼ젙??以묐났 ?곸슜?????덈떎.
 *   -> java 釉붾줉留??먭퀬 java-library 釉붾줉? ?쒓굅?쒕떎.
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
     * FIX: dependency-management ?곸슜 紐⑤뱢??Spring Boot BOM 媛뺤젣 import
     * - ?닿구 ?댁빞 org.springframework.boot:spring-boot-starter-xxx ?ㅼ씠 踰꾩쟾 ?놁씠??resolve ?⑸땲??
     *
     * 二쇱쓽
     * - ??釉붾줉? io.spring.dependency-management ?뚮윭洹몄씤??"紐낆떆?곸쑝濡? ?곸슜??紐⑤뱢?먮쭔 ?숈옉?⑸땲??
     * - 留뚯빟 ?대뼡 紐⑤뱢???ㅽ??곕? ?곕뒗?곕룄 dependency-management瑜??곸슜?섏? ?딆쑝硫?
     *   (?먮뒗 spring-boot ?뚮윭洹몄씤留??곸슜?섍퀬 ???뚮윭洹몄씤???곸슜?섏? ?딆쑝硫?
     *   踰꾩쟾 ?댁꽍???щ씪吏????덉쑝??紐⑤뱢蹂??뚮윭洹몄씤 ?곸슜 ?뺤콉???듭씪?섏꽭??
     */
    pluginManager.withPlugin("io.spring.dependency-management") {
        extensions.configure(DependencyManagementExtension::class.java) {
            imports {
                // spring-boot ?뚮윭洹몄씤 踰꾩쟾(= libs.versions.springBoot)怨?BOM 踰꾩쟾???숈씪?섍쾶 怨좎젙
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
     * Java 怨듯넻
     *
     * - java-library??java瑜??ы븿?섎?濡??ш린 ??踰덈쭔 ?ㅼ젙?쒕떎.
     * - UTF-8 / -parameters / JUnit Platform ?듭씪
     */
    pluginManager.withPlugin("java") {
        extensions.configure(org.gradle.api.plugins.JavaPluginExtension::class.java) {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType(JavaCompile::class.java).configureEach {
            options.encoding = "UTF-8"
            // 以묐났 異붽? 諛⑹?瑜??꾪빐 議댁옱 ?щ? 泥댄겕 ??異붽?
            if (!options.compilerArgs.contains("-parameters")) {
                options.compilerArgs.add("-parameters")
            }
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }
    }
}


