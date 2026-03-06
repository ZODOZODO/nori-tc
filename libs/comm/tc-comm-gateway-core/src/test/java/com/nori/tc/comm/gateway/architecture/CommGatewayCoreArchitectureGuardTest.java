package com.nori.tc.comm.gateway.architecture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * `tc-comm-gateway-core` 모듈의 아키텍처 경계를 검증하는 가드 테스트입니다.
 *
 * <p>핵심 규칙:</p>
 * <p>1) 코어 계층은 Kafka SDK를 직접 import하지 않습니다.</p>
 * <p>2) Kafka 호환 알고리즘이 필요하더라도 순수 Java 구현으로 유지합니다.</p>
 */
class CommGatewayCoreArchitectureGuardTest {

    /**
     * 게이트웨이 코어 메인 소스에서 Kafka SDK import 금지 규칙을 검증합니다.
     *
     * @throws IOException 파일 순회/읽기 실패 시 예외
     */
    @Test
    void commGatewayCoreMainSourcesShouldNotImportKafkaSdk() throws IOException {
        final List<String> violations = collectForbiddenTokenViolations(
                Path.of("src", "main", "java"),
                List.of("import org.apache.kafka.")
        );

        Assertions.assertTrue(
                violations.isEmpty(),
                () -> "tc-comm-gateway-core 에 Kafka SDK import가 남아 있습니다." + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations)
        );
    }

    /**
     * 코어 모듈의 Gradle 스크립트가 starter 모듈에 직접 의존하지 않는지 검증합니다.
     *
     * <p>설계 규칙:</p>
     * <p>- core 계층은 app 조립 책임을 갖는 starter 계층에 의존하면 안 됩니다.</p>
     * <p>- core는 domain/common/adapter 계약만 참조하고, starter 조합은 app 경계에서 수행합니다.</p>
     *
     * @throws IOException 스크립트 파일 읽기 실패 시 예외
     */
    @Test
    void commGatewayCoreBuildScriptShouldNotDependOnStarterModules() throws IOException {
        final Path buildScriptPath = Path.of("build.gradle.kts");
        final List<String> lines = Files.readAllLines(buildScriptPath, StandardCharsets.UTF_8);
        final List<String> violations = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i).trim();
            if (line.contains("project(\":libs:") && line.contains(":starter:")) {
                violations.add((i + 1) + ": " + line);
            }
        }

        Assertions.assertTrue(
                violations.isEmpty(),
                () -> "tc-comm-gateway-core build.gradle.kts 에 starter 직접 의존이 남아 있습니다."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations)
        );
    }

    /**
     * Java 소스에서 금지 토큰 사용 라인을 수집합니다.
     *
     * @param sourceRoot 검사 루트
     * @param forbiddenTokens 금지 토큰 목록
     * @return 위반 목록
     * @throws IOException 파일 순회 실패 시 예외
     */
    private static List<String> collectForbiddenTokenViolations(
            final Path sourceRoot,
            final List<String> forbiddenTokens
    ) throws IOException {
        final List<String> violations = new ArrayList<>();
        if (Files.notExists(sourceRoot)) {
            return violations;
        }

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> scanFile(sourceRoot, path, forbiddenTokens, violations));
        }
        return violations;
    }

    /**
     * 단일 파일을 UTF-8로 읽고 금지 토큰 포함 라인을 위반 목록에 누적합니다.
     *
     * @param sourceRoot 상대 경로 기준 루트
     * @param file 검사 대상 파일
     * @param forbiddenTokens 금지 토큰 목록
     * @param violations 누적 위반 목록
     */
    private static void scanFile(
            final Path sourceRoot,
            final Path file,
            final List<String> forbiddenTokens,
            final List<String> violations
    ) {
        try {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                final String line = lines.get(lineIndex);
                for (String token : forbiddenTokens) {
                    final String trimmedLine = line.stripLeading();
                    final boolean matched = token.startsWith("import ")
                            ? trimmedLine.startsWith(token)
                            : line.contains(token);
                    if (matched) {
                        violations.add(sourceRoot.relativize(file) + ":" + (lineIndex + 1) + " -> " + line.trim());
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("소스 파일 읽기 실패: " + file, ex);
        }
    }
}
