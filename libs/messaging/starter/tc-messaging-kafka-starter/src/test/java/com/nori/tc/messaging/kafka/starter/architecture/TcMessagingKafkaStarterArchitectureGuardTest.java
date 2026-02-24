package com.nori.tc.messaging.kafka.starter.architecture;

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
 * `tc-messaging-kafka-starter` 모듈의 역할 경계를 검증하는 가드 테스트입니다.
 *
 * <p>핵심 규칙:</p>
 * <p>1) Starter는 AutoConfiguration/Bean 조립 전용입니다.</p>
 * <p>2) `starter.contract`, `starter.runtime` 재사용 패키지는 존재하면 안 됩니다.</p>
 */
class TcMessagingKafkaStarterArchitectureGuardTest {

    /**
     * Starter 모듈에 제거 대상 패키지(runtime/contract) Java 소스가 남아 있지 않은지 검증합니다.
     *
     * @throws IOException 파일 순회 실패 시 예외
     */
    @Test
    void starterShouldNotContainLegacyRuntimeOrContractJavaSources() throws IOException {
        final Path mainJavaRoot = Path.of("src", "main", "java");
        final List<Path> forbiddenDirectories = List.of(
                mainJavaRoot.resolve(Path.of("com", "nori", "tc", "messaging", "kafka", "starter", "runtime")),
                mainJavaRoot.resolve(Path.of("com", "nori", "tc", "messaging", "kafka", "starter", "contract"))
        );

        final List<String> violations = new ArrayList<>();
        for (Path forbiddenDirectory : forbiddenDirectories) {
            if (Files.notExists(forbiddenDirectory)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(forbiddenDirectory)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(path -> violations.add(mainJavaRoot.relativize(path).toString()));
            }
        }

        Assertions.assertTrue(
                violations.isEmpty(),
                () -> "tc-messaging-kafka-starter 에 제거 대상 runtime/contract 소스가 남아 있습니다."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations)
        );
    }

    /**
     * Starter 메인 소스가 분리 전 패키지(`starter.runtime`, `starter.contract`)를 import/참조하지 않는지 검증합니다.
     *
     * @throws IOException 파일 순회/읽기 실패 시 예외
     */
    @Test
    void starterMainSourcesShouldNotReferenceLegacyStarterRuntimeOrContractPackages() throws IOException {
        final List<String> violations = collectForbiddenTokenViolations(
                Path.of("src", "main", "java"),
                List.of(
                        "com.nori.tc.messaging.kafka.starter.runtime",
                        "com.nori.tc.messaging.kafka.starter.contract"
                )
        );

        Assertions.assertTrue(
                violations.isEmpty(),
                () -> "tc-messaging-kafka-starter 메인 소스가 legacy starter.runtime/contract 패키지를 참조합니다."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations)
        );
    }

    /**
     * Java 소스에서 금지 토큰 포함 라인을 수집합니다.
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
     * 단일 Java 파일을 UTF-8로 읽고 금지 토큰 포함 라인을 위반 목록에 누적합니다.
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
            for (int i = 0; i < lines.size(); i++) {
                final String line = lines.get(i);
                for (String token : forbiddenTokens) {
                    final String trimmedLine = line.stripLeading();
                    final boolean matched = token.startsWith("import ")
                            ? trimmedLine.startsWith(token)
                            : line.contains(token);
                    if (matched) {
                        violations.add(sourceRoot.relativize(file) + ":" + (i + 1) + " -> " + line.trim());
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("소스 파일 읽기 실패: " + file, ex);
        }
    }
}
