package com.nori.tc.apps.commgateway.architecture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * `tc-comm-gateway-app` 모듈의 조합 책임(Composition Root) 규칙을 검증하는 가드 테스트입니다.
 *
 * <p>핵심 규칙:</p>
 * <p>1) 앱은 도메인 스타터 + 인프라 스타터(DB/Kafka) + 공통 로깅 모듈을 명시 의존해야 합니다.</p>
 * <p>2) 제거된 로그 스타터(`tc-log-starter`) 의존성이 다시 유입되면 안 됩니다.</p>
 */
class TcCommGatewayAppDependencyCompositionGuardTest {

    /**
     * 앱 Gradle 스크립트에 필수 조합 의존성이 모두 존재하는지 검증합니다.
     *
     * @throws IOException 스크립트 파일 읽기 실패 시 예외
     */
    @Test
    void buildScriptShouldDeclareRequiredCompositionDependencies() throws IOException {
        final String buildScript = Files.readString(Path.of("build.gradle.kts"), StandardCharsets.UTF_8);

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        buildScript.contains("implementation(project(\":libs:comm:starter:tc-comm-gateway-starter\"))"),
                        "Comm 앱은 tc-comm-gateway-starter를 명시 의존해야 합니다."
                ),
                () -> Assertions.assertTrue(
                        buildScript.contains("implementation(project(\":libs:db:starter:tc-db-postgres-jpa-starter\"))"),
                        "Comm 앱은 DB 스타터를 명시 의존해야 합니다."
                ),
                () -> Assertions.assertTrue(
                        buildScript.contains("implementation(project(\":libs:messaging:starter:tc-messaging-kafka-starter\"))"),
                        "Comm 앱은 Kafka 인프라 스타터를 명시 의존해야 합니다."
                ),
                () -> Assertions.assertTrue(
                        buildScript.contains("implementation(project(\":libs:common:tc-common-logging\"))"),
                        "Comm 앱은 공통 로깅 모듈을 명시 의존해야 합니다."
                )
        );
    }

    /**
     * 제거된 로그 스타터 의존성이 다시 추가되지 않았는지 검증합니다.
     *
     * @throws IOException 스크립트 파일 읽기 실패 시 예외
     */
    @Test
    void buildScriptShouldNotReferenceRemovedLogStarter() throws IOException {
        final String buildScript = Files.readString(Path.of("build.gradle.kts"), StandardCharsets.UTF_8);
        Assertions.assertFalse(
                buildScript.contains("tc-log-starter"),
                "제거된 tc-log-starter 의존성이 build.gradle.kts에 남아 있습니다."
        );
    }
}
