package com.nori.tc.common.task.execution.architecture;

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
 * `tc-common-task-execution` 모듈의 재시도 계약 의존 경계를 검증하는 가드 테스트입니다.
 *
 * <p>이 모듈은 Kafka SDK/구 Kafka 전용 consumer-runtime 계약에 의존하지 않고,
 * 중립 consumer-runtime 계약만 사용해야 합니다.</p>
 */
class CommonTaskExecutionArchitectureGuardTest {

    /**
     * 메인 소스에서 구 Kafka 전용 consumer-runtime 패키지 import가 없는지 검증합니다.
     *
     * @throws IOException 파일 순회/읽기 실패 시 예외
     */
    @Test
    void mainSourcesShouldNotImportLegacyKafkaProcessingPackage() throws IOException {
        final List<String> violations = collectForbiddenTokenViolations(
                Path.of("src", "main", "java"),
                List.of("import com.nori.tc.common.kafka.processing.")
        );

        Assertions.assertTrue(
                violations.isEmpty(),
                () -> "tc-common-task-execution 메인 소스에 구 kafka.processing import가 남아 있습니다."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations)
        );
    }

    /**
     * 테스트 소스에서도 구 Kafka 전용 consumer-runtime 패키지 import가 없는지 검증합니다.
     *
     * @throws IOException 파일 순회/읽기 실패 시 예외
     */
    @Test
    void testSourcesShouldNotImportLegacyKafkaProcessingPackage() throws IOException {
        final List<String> violations = collectForbiddenTokenViolations(
                Path.of("src", "test", "java"),
                List.of("import com.nori.tc.common.kafka.processing.")
        );

        Assertions.assertTrue(
                violations.isEmpty(),
                () -> "tc-common-task-execution 테스트 소스에 구 kafka.processing import가 남아 있습니다."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations)
        );
    }

    /**
     * 소스 루트 아래 Java 파일에서 금지 토큰 포함 라인을 수집합니다.
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
     * 단일 Java 파일을 UTF-8로 읽고 금지 토큰 포함 라인을 수집합니다.
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
