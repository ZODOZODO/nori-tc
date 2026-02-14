package com.nori.tc.comm.adapters.plugin.socket;

import com.nori.tc.comm.adapters.kafka.messaging.ui.GatewayUiJarfileTaskProcessor;
import com.nori.tc.comm.adapters.kafka.messaging.ui.GatewayUiTaskErrorCode;
import com.nori.tc.comm.adapters.kafka.messaging.ui.GatewayUiTaskResult;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.socket.plugin.GatewaySocketPluginRuntimeMutationPort;
import com.nori.tc.comm.gateway.socket.plugin.GatewaySocketPluginRuntimeProvider;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.jar.store.TcJarGatewayStore;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.jar.TcJarGateway;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Gateway SOCKET 플러그인 런타임 관리자입니다.
 *
 * <p>역할:</p>
 * <p>1) tc_jar_gateway 에 저장된 JAR 바이너리를 읽어 ClassLoader 로 로딩</p>
 * <p>2) SocketTypeHandler 구현체를 탐지/검증하여 설비별 런타임으로 보관</p>
 * <p>3) UI EQP_UPDATE_JARFILE 이벤트 시 설비 단위 reload 수행</p>
 * <p>4) 소켓 파이프라인/명령 디스패처에 플러그인 핸들러 제공</p>
 */
@Component
public class GatewaySocketPluginRuntimeManager
        implements GatewaySocketPluginRuntimeProvider,
        GatewaySocketPluginRuntimeMutationPort,
        GatewayUiJarfileTaskProcessor {

    /**
     * 런타임 동작 추적 로그입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewaySocketPluginRuntimeManager.class);

    /**
     * 기본 임시 JAR 저장 경로입니다.
     */
    private static final Path DEFAULT_PLUGIN_TEMP_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"),
            "nori-tc",
            "comm-gateway-plugin-runtime"
    );

    /**
     * 설비 기본 정보 조회 포트입니다.
     */
    private final TcEqpStore eqpStore;

    /**
     * gateway 플러그인 JAR 조회 포트입니다.
     */
    private final TcJarGatewayStore jarGatewayStore;

    /**
     * 플러그인 런타임 프로퍼티입니다.
     */
    private final GatewaySocketPluginRuntimeProperties properties;

    /**
     * 설비별 플러그인 런타임 스냅샷입니다.
     *
     * <p>동시성 안전을 위해 불변 맵(Map.copyOf) + 원자 참조 교체 전략을 사용합니다.</p>
     */
    private final AtomicReference<Map<String, PluginRuntime>> runtimeByEqpIdRef = new AtomicReference<>(Map.of());

    /**
     * 런타임 관리자 의존성을 주입받습니다.
     *
     * @param eqpStore 설비 조회 store
     * @param jarGatewayStore gateway JAR 조회 store
     * @param properties 플러그인 런타임 프로퍼티
     */
    public GatewaySocketPluginRuntimeManager(
            final TcEqpStore eqpStore,
            final TcJarGatewayStore jarGatewayStore,
            final GatewaySocketPluginRuntimeProperties properties
    ) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        this.jarGatewayStore = Objects.requireNonNull(jarGatewayStore, "jarGatewayStore is null");
        this.properties = Objects.requireNonNull(properties, "properties is null");
    }

    /**
     * 애플리케이션 기동 직후 preload 정책을 수행합니다.
     */
    @PostConstruct
    public void initialize() {
        if (!properties.isLoadOnStartup()) {
            log.info("Gateway socket plugin preload skipped. loadOnStartup=false");
            return;
        }

        try {
            preloadAllFromDb();
        } catch (RuntimeException ex) {
            log.error("Gateway socket plugin preload failed on startup.", ex);
            if (properties.isFailFastOnStartup()) {
                throw ex;
            }
        }
    }

    /**
     * 애플리케이션 종료 시 classloader/임시파일 자원을 정리합니다.
     */
    @PreDestroy
    public void shutdown() {
        final Map<String, PluginRuntime> previous = runtimeByEqpIdRef.getAndSet(Map.of());
        closeRuntimeMapQuietly(previous);
        log.info("Gateway socket plugin runtime manager shutdown completed. closedRuntimeCount={}", previous.size());
    }

    /**
     * 설비별 활성 플러그인 핸들러를 조회합니다.
     *
     * @param eqpId 설비 ID
     * @return 플러그인 핸들러(Optional)
     */
    @Override
    public Optional<SocketTypeHandler> findByEqpId(final String eqpId) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        if (normalizedEqpId == null) {
            return Optional.empty();
        }

        final PluginRuntime runtime = runtimeByEqpIdRef.get().get(normalizedEqpId);
        return runtime == null ? Optional.empty() : Optional.of(runtime.handler());
    }

    /**
     * 특정 설비 플러그인 런타임을 재로딩합니다.
     *
     * @param eqpId 설비 ID
     */
    @Override
    public void reloadByEqpId(final String eqpId) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        if (normalizedEqpId == null) {
            throw new IllegalArgumentException("eqpId is required");
        }

        final TcEqp eqp = eqpStore.findByEqpId(normalizedEqpId).orElseThrow(
                () -> new IllegalStateException("Equipment not found for gateway plugin reload. eqpId=" + normalizedEqpId)
        );

        if (eqp.commInterface() != ProtocolType.SOCKET) {
            throw new IllegalStateException("Gateway plugin reload supports SOCKET only. eqpId=" + normalizedEqpId);
        }
        if (eqp.eqpKey() == null || eqp.eqpKey() <= 0L) {
            throw new IllegalStateException("Invalid eqpKey for gateway plugin reload. eqpId=" + normalizedEqpId);
        }

        final TcJarGateway jarRow = jarGatewayStore.findByEqpKey(eqp.eqpKey()).orElseThrow(
                () -> new IllegalStateException(
                        "tc_jar_gateway not found. eqpId=" + normalizedEqpId + ", eqpKey=" + eqp.eqpKey()
                )
        );

        if (jarRow.jarFile() == null || jarRow.jarFile().length == 0) {
            throw new IllegalStateException("tc_jar_gateway.jar_file is empty. eqpId=" + normalizedEqpId);
        }

        final PluginRuntime newRuntime = buildPluginRuntime(
                normalizedEqpId,
                jarRow.jarFileName(),
                jarRow.jarFile()
        );

        swapRuntime(normalizedEqpId, newRuntime);
    }

    /**
     * DB 기준으로 전체 설비의 플러그인 런타임을 preload 합니다.
     */
    public void preloadAllFromDb() {
        final Map<String, PluginRuntime> nextRuntimeMap = new LinkedHashMap<>();

        int scannedEqpCount = 0;
        int socketEqpCount = 0;
        int loadedCount = 0;
        int noJarCount = 0;
        int failedCount = 0;

        int offset = 0;
        final int pageSize = properties.getPageSize();

        while (true) {
            final List<TcEqp> page = eqpStore.findAll(PageRequest.of(offset, pageSize));
            if (page.isEmpty()) {
                break;
            }

            for (TcEqp eqp : page) {
                scannedEqpCount++;

                if (eqp == null || eqp.eqpKey() == null || eqp.eqpKey() <= 0L) {
                    if (log.isDebugEnabled()) {
                        log.debug("Gateway plugin preload skipped due to invalid eqp row. eqp={}", eqp);
                    }
                    continue;
                }

                final String eqpId = normalizeEqpId(eqp.eqpId());
                if (eqpId == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Gateway plugin preload skipped due to blank eqpId. eqpKey={}", eqp.eqpKey());
                    }
                    continue;
                }

                if (eqp.commInterface() != ProtocolType.SOCKET) {
                    continue;
                }
                socketEqpCount++;

                final Optional<TcJarGateway> jarOptional = jarGatewayStore.findByEqpKey(eqp.eqpKey());
                if (jarOptional.isEmpty()) {
                    noJarCount++;
                    if (log.isDebugEnabled()) {
                        log.debug("Gateway plugin preload skipped because jar is absent. eqpId={}, eqpKey={}",
                                eqpId,
                                eqp.eqpKey());
                    }
                    continue;
                }

                final TcJarGateway jarRow = jarOptional.get();
                if (jarRow.jarFile() == null || jarRow.jarFile().length == 0) {
                    noJarCount++;
                    if (log.isDebugEnabled()) {
                        log.debug("Gateway plugin preload skipped because jar bytes are empty. eqpId={}, eqpKey={}",
                                eqpId,
                                eqp.eqpKey());
                    }
                    continue;
                }

                try {
                    final PluginRuntime runtime = buildPluginRuntime(
                            eqpId,
                            jarRow.jarFileName(),
                            jarRow.jarFile()
                    );

                    final PluginRuntime duplicate = nextRuntimeMap.put(eqpId, runtime);
                    if (duplicate != null) {
                        duplicate.closeQuietly();
                    }
                    loadedCount++;
                } catch (RuntimeException ex) {
                    failedCount++;
                    if (properties.isFailFastOnStartup()) {
                        throw new IllegalStateException(
                                "Gateway plugin preload failed. eqpId=" + eqpId + ", eqpKey=" + eqp.eqpKey(),
                                ex
                        );
                    }
                    log.warn("Gateway plugin preload skipped due to validation/load failure. eqpId={}, eqpKey={}",
                            eqpId,
                            eqp.eqpKey(),
                            ex);
                }
            }

            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }

        replaceAllRuntimes(nextRuntimeMap);

        log.info(
                "Gateway plugin preload completed. scannedEqpCount={}, socketEqpCount={}, loadedCount={}, noJarCount={}, failedCount={}, activeRuntimeCount={}",
                scannedEqpCount,
                socketEqpCount,
                loadedCount,
                noJarCount,
                failedCount,
                runtimeByEqpIdRef.get().size()
        );
    }

    /**
     * UI EQP_UPDATE_JARFILE 이벤트 처리 시 플러그인 런타임 리로드를 수행합니다.
     *
     * @param message UI task 원본 메시지
     * @param equipmentInfo 검증된 설비 정보
     * @return UI PASS/FAIL 결과
     */
    @Override
    public GatewayUiTaskResult process(
            final KafkaUiTaskMessage message,
            final GatewayEquipmentInfo equipmentInfo
    ) {
        final String eqpIdFromEquipment = equipmentInfo == null ? null : normalizeEqpId(equipmentInfo.equipmentId());
        final String eqpIdFromMessage = message == null || message.data() == null
                ? null
                : normalizeEqpId(message.data().eqpId());
        final String eqpId = eqpIdFromEquipment != null ? eqpIdFromEquipment : eqpIdFromMessage;

        if (eqpId == null) {
            log.info("Gateway plugin runtime reload rejected. reason=eqpId missing");
            return GatewayUiTaskResult.fail(
                    GatewayUiTaskErrorCode.JARFILE_TASK_FAILED,
                    "Gateway plugin runtime reload failed: eqpId is required"
            );
        }

        final String traceId = message == null || message.metadata() == null
                ? "N/A"
                : String.valueOf(message.metadata().traceId());

        try {
            reloadByEqpId(eqpId);
            log.info("Gateway plugin runtime reload completed by UI jarfile task. eqpId={}, traceId={}", eqpId, traceId);
            return GatewayUiTaskResult.pass();
        } catch (RuntimeException ex) {
            log.error("Gateway plugin runtime reload failed by UI jarfile task. eqpId={}, traceId={}", eqpId, traceId, ex);
            return GatewayUiTaskResult.fail(
                    GatewayUiTaskErrorCode.JARFILE_TASK_FAILED,
                    "Gateway plugin runtime reload failed: " + safeMessage(ex)
            );
        }
    }

    /**
     * 특정 설비용 플러그인 런타임을 구성합니다.
     *
     * @param eqpId 설비 ID
     * @param jarFileName JAR 파일명
     * @param jarBytes JAR 바이너리
     * @return 완성된 플러그인 런타임
     */
    private PluginRuntime buildPluginRuntime(
            final String eqpId,
            final String jarFileName,
            final byte[] jarBytes
    ) {
        final String normalizedJarFileName = normalizeJarFileName(jarFileName);
        final Path jarPath = writePluginJarToTempFile(eqpId, normalizedJarFileName, jarBytes);
        URLClassLoader classLoader = null;

        try {
            classLoader = createPluginClassLoader(jarPath);
            final List<String> classNames = discoverClassNames(jarBytes);

            if (log.isDebugEnabled()) {
                log.debug("Gateway plugin class discovery completed. eqpId={}, classCount={}", eqpId, classNames.size());
            }

            final SocketTypeHandler handler = discoverSingleHandler(classLoader, classNames, eqpId);

            log.info("Gateway plugin runtime validated. eqpId={}, jarFileName={}, socketType={}, handlerClass={}",
                    eqpId,
                    normalizedJarFileName,
                    handler.socketType(),
                    handler.getClass().getName());

            return new PluginRuntime(eqpId, normalizedJarFileName, jarPath, classLoader, handler);
        } catch (RuntimeException ex) {
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException closeEx) {
                    log.warn("Gateway plugin classloader close failed while rollback. eqpId={}, jarFileName={}",
                            eqpId,
                            normalizedJarFileName,
                            closeEx);
                }
            }
            try {
                Files.deleteIfExists(jarPath);
            } catch (IOException deleteEx) {
                log.warn("Gateway plugin temp jar delete failed while rollback. eqpId={}, path={}", eqpId, jarPath, deleteEx);
            }
            throw ex;
        }
    }

    /**
     * JAR 엔트리를 순회하여 클래스명을 수집합니다.
     *
     * @param jarBytes JAR 바이너리
     * @return 클래스명 목록
     */
    private List<String> discoverClassNames(final byte[] jarBytes) {
        final List<String> classNames = new ArrayList<>();
        try (JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            JarEntry entry;
            while ((entry = jarInputStream.getNextJarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                final String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                classNames.add(toClassName(name));
            }
            return classNames;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read gateway plugin jar entries", ex);
        }
    }

    /**
     * 클래스 로더로 후보 클래스를 로딩하여 SocketTypeHandler 구현체 1개를 선택합니다.
     *
     * @param classLoader 플러그인 클래스 로더
     * @param classNames JAR 클래스명 목록
     * @param eqpId 설비 ID
     * @return 검증된 핸들러 구현체
     */
    private SocketTypeHandler discoverSingleHandler(
            final URLClassLoader classLoader,
            final List<String> classNames,
            final String eqpId
    ) {
        final List<SocketTypeHandler> handlers = new ArrayList<>();

        for (String className : classNames) {
            final Class<?> rawClass;
            try {
                rawClass = Class.forName(className, false, classLoader);
            } catch (Throwable ex) {
                if (log.isDebugEnabled()) {
                    log.debug("Gateway plugin class load skipped. eqpId={}, className={}", eqpId, className, ex);
                }
                continue;
            }

            if (!SocketTypeHandler.class.isAssignableFrom(rawClass)) {
                continue;
            }
            if (rawClass.isInterface() || Modifier.isAbstract(rawClass.getModifiers())) {
                continue;
            }

            handlers.add(instantiateHandler(rawClass));
        }

        if (handlers.isEmpty()) {
            throw new IllegalStateException("No SocketTypeHandler implementation found in gateway plugin jar. eqpId=" + eqpId);
        }
        if (handlers.size() > 1) {
            throw new IllegalStateException(
                    "Multiple SocketTypeHandler implementations found in gateway plugin jar. eqpId=" + eqpId
            );
        }

        final SocketTypeHandler handler = handlers.get(0);
        final String declaredSocketType = handler.socketType();
        if (declaredSocketType == null || declaredSocketType.isBlank()) {
            throw new IllegalStateException("Plugin handler.socketType() must not be blank. eqpId=" + eqpId);
        }

        return handler;
    }

    /**
     * 핸들러 클래스를 리플렉션으로 인스턴스화합니다.
     *
     * @param rawClass 핸들러 구현 클래스
     * @return 생성된 핸들러 인스턴스
     */
    private SocketTypeHandler instantiateHandler(final Class<?> rawClass) {
        try {
            final Constructor<?> constructor = rawClass.getDeclaredConstructor();
            if (!constructor.canAccess(null)) {
                constructor.setAccessible(true);
            }
            return (SocketTypeHandler) constructor.newInstance();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to instantiate plugin handler class: " + rawClass.getName(), ex);
        }
    }

    /**
     * 플러그인 JAR 바이너리를 임시 파일로 저장합니다.
     *
     * @param eqpId 설비 ID
     * @param jarFileName JAR 파일명
     * @param jarBytes JAR 바이너리
     * @return 저장된 임시 파일 경로
     */
    private Path writePluginJarToTempFile(
            final String eqpId,
            final String jarFileName,
            final byte[] jarBytes
    ) {
        try {
            final Path tempRoot = resolveTempRoot();
            Files.createDirectories(tempRoot);

            final String safeFileName = eqpId + "-" + System.nanoTime() + "-" + jarFileName;
            final Path jarPath = tempRoot.resolve(safeFileName);

            Files.write(
                    jarPath,
                    jarBytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            return jarPath;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write gateway plugin jar to temp file. eqpId=" + eqpId, ex);
        }
    }

    /**
     * 플러그인 전용 URLClassLoader 를 생성합니다.
     *
     * @param jarPath 임시 JAR 파일 경로
     * @return 생성된 URLClassLoader
     */
    private URLClassLoader createPluginClassLoader(final Path jarPath) {
        try {
            final URL[] urls = new URL[]{jarPath.toUri().toURL()};
            return new URLClassLoader(urls, GatewaySocketPluginRuntimeManager.class.getClassLoader());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create gateway plugin class loader. jarPath=" + jarPath, ex);
        }
    }

    /**
     * 실제 사용할 임시 루트 디렉터리를 계산합니다.
     *
     * @return 임시 루트 디렉터리
     */
    private Path resolveTempRoot() {
        final String configured = properties.getTempRootDir();
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_PLUGIN_TEMP_ROOT;
        }
        return Path.of(configured.trim());
    }

    /**
     * 단일 설비 런타임을 원자적으로 교체합니다.
     *
     * @param eqpId 설비 ID
     * @param newRuntime 신규 런타임
     */
    private void swapRuntime(final String eqpId, final PluginRuntime newRuntime) {
        PluginRuntime previousRuntime;
        while (true) {
            final Map<String, PluginRuntime> current = runtimeByEqpIdRef.get();
            final Map<String, PluginRuntime> next = new LinkedHashMap<>(current);
            previousRuntime = next.put(eqpId, newRuntime);
            if (runtimeByEqpIdRef.compareAndSet(current, Map.copyOf(next))) {
                break;
            }
        }

        if (previousRuntime != null) {
            previousRuntime.closeQuietly();
        }

        log.info("Gateway plugin runtime swapped. eqpId={}, jarFileName={}, handlerClass={}",
                eqpId,
                newRuntime.jarFileName(),
                newRuntime.handler().getClass().getName());
    }

    /**
     * 전체 런타임 맵을 새 스냅샷으로 교체합니다.
     *
     * @param nextRuntimeMap 신규 런타임 맵
     */
    private void replaceAllRuntimes(final Map<String, PluginRuntime> nextRuntimeMap) {
        final Map<String, PluginRuntime> previous = runtimeByEqpIdRef.getAndSet(Map.copyOf(nextRuntimeMap));
        closeRuntimeMapQuietly(previous);
    }

    /**
     * 런타임 맵 전체를 예외 없이 정리합니다.
     *
     * @param runtimeMap 정리 대상 런타임 맵
     */
    private static void closeRuntimeMapQuietly(final Map<String, PluginRuntime> runtimeMap) {
        if (runtimeMap == null || runtimeMap.isEmpty()) {
            return;
        }
        for (PluginRuntime runtime : runtimeMap.values()) {
            if (runtime == null) {
                continue;
            }
            runtime.closeQuietly();
        }
    }

    /**
     * eqpId 문자열을 trim/검증하여 정규화합니다.
     *
     * @param eqpId 원본 eqpId
     * @return 정규화된 eqpId(유효하지 않으면 null)
     */
    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null) {
            return null;
        }
        final String normalized = eqpId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * JAR 파일명을 기본값 포함 형태로 정규화합니다.
     *
     * @param jarFileName 원본 파일명
     * @return 정규화된 파일명
     */
    private static String normalizeJarFileName(final String jarFileName) {
        if (jarFileName == null || jarFileName.trim().isEmpty()) {
            return "gateway-socket-plugin.jar";
        }
        return jarFileName.trim();
    }

    /**
     * JAR 엔트리 경로를 클래스명(FQCN)으로 변환합니다.
     *
     * @param entryName JAR 엔트리명
     * @return FQCN
     */
    private static String toClassName(final String entryName) {
        return entryName
                .replace('/', '.')
                .substring(0, entryName.length() - ".class".length());
    }

    /**
     * 예외 메시지를 안전하게 문자열로 변환합니다.
     *
     * @param ex 대상 예외
     * @return 메시지(없으면 예외 클래스명)
     */
    private static String safeMessage(final Throwable ex) {
        if (ex == null) {
            return "unknown";
        }
        final String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    /**
     * 로딩된 플러그인 런타임 단위입니다.
     *
     * @param eqpId 설비 ID
     * @param jarFileName JAR 파일명
     * @param jarPath 임시 JAR 경로
     * @param classLoader 플러그인 전용 클래스 로더
     * @param handler 활성 핸들러 인스턴스
     */
    private record PluginRuntime(
            String eqpId,
            String jarFileName,
            Path jarPath,
            URLClassLoader classLoader,
            SocketTypeHandler handler
    ) {

        /**
         * classloader/임시파일을 안전하게 정리합니다.
         */
        private void closeQuietly() {
            try {
                classLoader.close();
            } catch (IOException ex) {
                log.warn("Gateway plugin classloader close failed. eqpId={}, jarFileName={}", eqpId, jarFileName, ex);
            }

            try {
                Files.deleteIfExists(jarPath);
            } catch (IOException ex) {
                log.warn("Gateway plugin temp jar delete failed. eqpId={}, path={}", eqpId, jarPath, ex);
            }
        }
    }
}
