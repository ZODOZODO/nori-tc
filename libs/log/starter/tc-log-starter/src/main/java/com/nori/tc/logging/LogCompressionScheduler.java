package com.nori.tc.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * 로그 압축/정리 스케줄러.
 *
 * 요구 사항:
 * - 최근 N일 로그는 .log 유지
 * - N일이 지난 로그는 .gz로 압축
 * - 오래된 .log.gz는 보관 기간 기준으로 삭제
 */
public final class LogCompressionScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LogCompressionScheduler.class);
    private static final Pattern DATED_LOG_PATTERN = Pattern.compile(".*\\.(\\d{4}-\\d{2}-\\d{2})\\.\\d+\\.log(\\.gz)?$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path appDir;
    private final Path eqpDir;
    private final int afterDays;
    private final int scanIntervalMinutes;
    private final int appMaxHistoryDays;
    private final int eqpMaxHistoryDays;

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    /**
     * 로깅 모듈 구성 요소를 초기화합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param properties 모듈 설정 값 객체
     * @param environment 런타임 환경 설정 정보
     */
    public LogCompressionScheduler(
            final LogCompressionProperties properties,
            final Environment environment
    ) {
        Objects.requireNonNull(properties, "properties is null");
        Objects.requireNonNull(environment, "environment is null");

        final String baseDir = environment.getProperty("tc.logging.file-dir", "logs");
        final int resolvedAfterDays = Math.max(1, properties.getAfterDays());
        final int resolvedScanMinutes = Math.max(1, properties.getScanIntervalMinutes());
        final int resolvedAppHistory = environment.getProperty("tc.logging.app.max-history", Integer.class, 14);
        final int resolvedEqpHistory = environment.getProperty("tc.logging.eqp.max-history", Integer.class, 14);

        this.appDir = Paths.get(baseDir, "app");
        this.eqpDir = Paths.get(baseDir, "eqp");
        this.afterDays = resolvedAfterDays;
        this.scanIntervalMinutes = resolvedScanMinutes;
        this.appMaxHistoryDays = Math.max(1, resolvedAppHistory);
        this.eqpMaxHistoryDays = Math.max(1, resolvedEqpHistory);
    }

    /**
     * 로깅 모듈 실행 환경을 초기화하고 기동합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     */
    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread thread = new Thread(r, "tc-log-compressor");
            thread.setDaemon(true);
            return thread;
        });

        // 최초 1분 후부터 주기적으로 실행
        scheduler.scheduleWithFixedDelay(this::runSafely, 1, scanIntervalMinutes, TimeUnit.MINUTES);
        log.info("Log compression scheduler started. afterDays={}, scanIntervalMinutes={}",
                afterDays, scanIntervalMinutes);
    }

    /**
     * 로깅 모듈 리소스를 정리하고 종료합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     */
    @Override
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * 로깅 모듈 현재 상태를 확인합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 로깅 모듈에서 필요한 값을 조회합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @return 로깅 모듈 처리 결과
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * 로깅 모듈 실행 흐름을 처리합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     */
    private void runSafely() {
        try {
            compressAndCleanup(appDir, appMaxHistoryDays);
            compressAndCleanup(eqpDir, eqpMaxHistoryDays);
        } catch (Exception ex) {
            log.warn("Log compression job failed.", ex);
        }
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param dir 파일/디렉터리 경로 정보
     * @param maxHistoryDays 로깅 모듈 처리에 사용하는 입력 값
     */
    private void compressAndCleanup(final Path dir, final int maxHistoryDays) throws IOException {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                final String name = path.getFileName().toString();
                try {
                    if (name.endsWith(".log")) {
                        if (isOlderThan(path, afterDays)) {
                            compress(path);
                        }
                        return;
                    }
                    if (name.endsWith(".log.gz")) {
                        if (isOlderThan(path, maxHistoryDays)) {
                            Files.deleteIfExists(path);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Failed to process log file. file={}", path, ex);
                }
            });
        }
    }

    /**
     * 로깅 모듈 현재 상태를 확인합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param path 파일/디렉터리 경로 정보
     * @param keepDays 로깅 모듈 처리에 사용하는 입력 값
     * @return 처리 성공 여부
     */
    private boolean isOlderThan(final Path path, final int keepDays) throws IOException {
        final LocalDate fileDate = extractDate(path);
        if (fileDate == null) {
            return false;
        }
        final int days = Math.max(1, keepDays);
        final LocalDate cutoff = LocalDate.now().minusDays(days - 1L);
        return fileDate.isBefore(cutoff);
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param path 파일/디렉터리 경로 정보
     * @return 로깅 모듈 처리 결과
     */
    private LocalDate extractDate(final Path path) throws IOException {
        final String name = path.getFileName().toString();
        final Matcher matcher = DATED_LOG_PATTERN.matcher(name);
        if (matcher.matches()) {
            return LocalDate.parse(matcher.group(1), DATE_FORMATTER);
        }
        final Instant lastModified = Files.getLastModifiedTime(path).toInstant();
        return LocalDate.ofInstant(lastModified, ZoneId.systemDefault());
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param logFile 파일/디렉터리 경로 정보
     */
    private void compress(final Path logFile) throws IOException {
        final Path gzPath = Paths.get(logFile.toString() + ".gz");
        if (Files.exists(gzPath)) {
            Files.deleteIfExists(logFile);
            return;
        }

        try (InputStream input = Files.newInputStream(logFile);
             OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzPath))) {
            input.transferTo(output);
        }

        Files.deleteIfExists(logFile);
    }
}
