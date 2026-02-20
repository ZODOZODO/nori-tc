package com.nori.tc.business.adapters.plugin.workflow;

import com.nori.tc.business.core.workflow.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.BusinessWorkflowActionRegistryBuilder;
import com.nori.tc.business.core.workflow.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.business.core.workflow.BusinessWorkflowPluginRuntimeProvider;
import com.nori.tc.business.core.workflow.MesActionExecutor;
import com.nori.tc.business.core.workflow.SecsActionExecutor;
import com.nori.tc.business.core.workflow.SocketActionExecutor;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.jar.store.TcJarBusinessStore;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.jar.TcJarBusiness;
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
 * 설비별 플러그인 런타임 관리자입니다.
 *
 * <p>역할:</p>
 * <p>1) tc_jar_business에서 JAR 바이너리를 읽어 ClassLoader로 로딩</p>
 * <p>2) Executor(@TcAction) 스캔 후 액션 레지스트리 구성</p>
 * <p>3) 검증 성공 시 eqpId 기준으로 plugin runtime 원자적(atomic) 스왑</p>
 *
 * <p>Step 14 확장:</p>
 * <p>- 기동 시 plugin runtime preload 지원</p>
 * <p>- preload fail-fast 정책/페이지 조회 크기 설정 지원</p>
 */
@Component
public class BusinessWorkflowPluginRuntimeManager
        implements BusinessWorkflowPluginRuntimeProvider, BusinessWorkflowPluginRuntimeMutationPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowPluginRuntimeManager.class);

    /**
     * 리로드 시작 표준 로그 이벤트명입니다.
     */
    private static final String RELOAD_EVENT_STARTED = "PLUGIN_RELOAD_STARTED";

    /**
     * 리로드 적용 완료 표준 로그 이벤트명입니다.
     */
    private static final String RELOAD_EVENT_APPLIED = "PLUGIN_RELOAD_APPLIED";

    /**
     * 리로드 롤백 표준 로그 이벤트명입니다.
     */
    private static final String RELOAD_EVENT_ROLLED_BACK = "PLUGIN_RELOAD_ROLLED_BACK";

    /**
     * jar 파일명이 비어 있을 때 사용할 기본명입니다.
     */
    private static final String DEFAULT_PLUGIN_JAR_FILE_NAME = "workflow-plugin.jar";

    /**
     * 플러그인 JAR 임시 파일을 저장할 루트 디렉터리입니다.
     */
    private static final Path PLUGIN_TEMP_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"),
            "nori-tc",
            "business-plugin-runtime"
    );

    private final TcEqpStore eqpStore;
    private final TcJarBusinessStore jarBusinessStore;
    private final BusinessWorkflowPluginRuntimeProperties properties;

    /**
     * eqpId -> PluginRuntime 매핑 스냅샷입니다.
     *
     * <p>항상 불변 맵(Map.copyOf)으로 저장하며, 갱신은 원자적으로 교체합니다.</p>
     */
    private final AtomicReference<Map<String, PluginRuntime>> runtimeByEqpIdRef = new AtomicReference<>(Map.of());

    /**
     * 플러그인 런타임 관리자 의존성을 주입받습니다.
     *
     * @param eqpStore eqp 조회 store
     * @param jarBusinessStore business jar store
     * @param properties plugin runtime properties
     */
    public BusinessWorkflowPluginRuntimeManager(
            final TcEqpStore eqpStore,
            final TcJarBusinessStore jarBusinessStore,
            final BusinessWorkflowPluginRuntimeProperties properties
    ) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        this.jarBusinessStore = Objects.requireNonNull(jarBusinessStore, "jarBusinessStore is null");
        this.properties = Objects.requireNonNull(properties, "properties is null");
    }

    /**
     * 애플리케이션 기동 직후 preload 정책을 수행합니다.
     */
    @PostConstruct
    public void initialize() {
        if (!properties.isLoadOnStartup()) {
            log.info("Plugin runtime preload skipped. loadOnStartup=false");
            return;
        }

        try {
            preloadAllFromDb();
        } catch (RuntimeException ex) {
            log.error("Plugin runtime preload failed on startup.", ex);
            if (properties.isFailFastOnStartup()) {
                throw ex;
            }
        }
    }

    /**
     * 애플리케이션 종료 시 플러그인 런타임 리소스를 정리합니다.
     *
     * <p>URLClassLoader/임시 JAR 파일이 누적되지 않도록, 종료 시점에
     * 전체 런타임 스냅샷을 비우고 자원을 닫습니다.</p>
     */
    @PreDestroy
    public void shutdown() {
        final Map<String, PluginRuntime> previous = runtimeByEqpIdRef.getAndSet(Map.of());
        closeRuntimeMapQuietly(previous);
        log.info("Business workflow plugin runtime manager shutdown completed. closedRuntimeCount={}",
                previous.size());
    }

    /**
     * findRegistryByEqpId 기능을 수행합니다.
     *
     * @param eqpId 입력 값
     * @return 처리 결과
     */

    @Override
    public Optional<BusinessWorkflowActionRegistry> findRegistryByEqpId(final String eqpId) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        if (normalizedEqpId == null) {
            return Optional.empty();
        }
        final PluginRuntime runtime = runtimeByEqpIdRef.get().get(normalizedEqpId);
        if (runtime == null) {
            return Optional.empty();
        }
        return Optional.of(runtime.registry());
    }

    /**
     * reloadByEqpId 기능을 수행합니다.
     *
     * @param eqpId 입력 값
     */

    @Override
    public void reloadByEqpId(final String eqpId) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        if (normalizedEqpId == null) {
            throw new IllegalArgumentException("eqpId is required");
        }

        final TcEqp eqp = eqpStore.findByEqpId(normalizedEqpId)
                .orElseThrow(() -> new IllegalStateException("tc_eqp not found. eqpId=" + normalizedEqpId));
        if (eqp.eqpKey() == null || eqp.eqpKey() <= 0L) {
            throw new IllegalStateException("Invalid eqpKey for plugin reload. eqpId=" + normalizedEqpId);
        }

        final boolean hadPreviousRuntime = runtimeByEqpIdRef.get().containsKey(normalizedEqpId);
        logReloadStarted(normalizedEqpId, eqp.eqpKey(), hadPreviousRuntime);

        final TcJarBusiness jarBusiness = jarBusinessStore.findByEqpKey(eqp.eqpKey()).orElse(null);
        if (jarBusiness == null || jarBusiness.jarFile() == null || jarBusiness.jarFile().length == 0) {
            /*
             * Phase 3 "액션 삭제 fallback" 규칙:
             * - JAR 행이 삭제되었거나 파일이 비어 있으면 플러그인 런타임을 제거합니다.
             * - 이후 실행기는 core registry만 사용하게 되어 자동 fallback 됩니다.
             */
            removeRuntimeByEqpId(normalizedEqpId, "JAR_ABSENT_OR_EMPTY");
            return;
        }

        try {
            final PluginRuntime newRuntime = buildPluginRuntime(
                    normalizedEqpId,
                    normalizeJarFileName(jarBusiness.jarFileName()),
                    jarBusiness.jarFile()
            );
            swapRuntime(normalizedEqpId, newRuntime);
            logReloadApplied(
                    normalizedEqpId,
                    "UPSERT",
                    true,
                    newRuntime.jarFileName(),
                    newRuntime.registry().size(),
                    null
            );
        } catch (RuntimeException ex) {
            /*
             * build/swap 예외 시 기존 runtime은 유지됩니다.
             * (swap 이전 실패, 또는 CAS 교체 실패 재시도 중 예외 없음)
             */
            final boolean runtimePreserved = runtimeByEqpIdRef.get().containsKey(normalizedEqpId);
            logReloadRolledBack(normalizedEqpId, runtimePreserved, ex);
            throw ex;
        }
    }

    /**
     * 특정 eqpId의 플러그인 런타임을 제거합니다.
     *
     * <p>UI EQP_DELETE 처리 경로에서 호출되며,
     * 이미 런타임이 없는 경우에는 예외 없이 무시합니다.</p>
     *
     * @param eqpId 제거 대상 설비 ID
     */
    @Override
    public void removeByEqpId(final String eqpId) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        if (normalizedEqpId == null) {
            throw new IllegalArgumentException("eqpId is required");
        }
        removeRuntimeByEqpId(normalizedEqpId, "UI_DELETE");
    }

    /**
     * DB 기준으로 전체 플러그인 런타임을 preload/재조립합니다.
     *
     * <p>검증 성공한 eqp만 새 스냅샷에 포함하며, 최종적으로 기존 스냅샷과 원자적으로 교체합니다.</p>
     */
    public void preloadAllFromDb() {
        final List<TcEqp> eqps = loadAllEqps();

        int scannedEqpCount = 0;
        int noJarCount = 0;
        int loadedCount = 0;
        int failedCount = 0;

        final Map<String, PluginRuntime> nextRuntimeMap = new LinkedHashMap<>();
        try {
            for (TcEqp eqp : eqps) {
                if (eqp == null) {
                    continue;
                }
                scannedEqpCount++;

                final String eqpId = normalizeEqpId(eqp.eqpId());
                if (eqpId == null || eqp.eqpKey() == null || eqp.eqpKey() <= 0L) {
                    if (log.isDebugEnabled()) {
                        log.debug("Plugin preload skipped due to invalid eqp row. eqpId={}, eqpKey={}",
                                eqp == null ? null : eqp.eqpId(),
                                eqp == null ? null : eqp.eqpKey());
                    }
                    continue;
                }

                final Optional<TcJarBusiness> jarBusinessOptional = jarBusinessStore.findByEqpKey(eqp.eqpKey());
                final TcJarBusiness jarBusiness = jarBusinessOptional.orElse(null);
                if (jarBusiness == null
                        || jarBusiness.jarFile() == null
                        || jarBusiness.jarFile().length == 0) {
                    noJarCount++;
                    if (log.isDebugEnabled()) {
                        log.debug("Plugin preload skipped because jar is absent. eqpId={}, eqpKey={}", eqpId, eqp.eqpKey());
                    }
                    continue;
                }
                try {
                    final PluginRuntime runtime = buildPluginRuntime(
                            eqpId,
                            normalizeJarFileName(jarBusiness.jarFileName()),
                            jarBusiness.jarFile()
                    );
                    final PluginRuntime duplicate = nextRuntimeMap.put(eqpId, runtime);
                    if (duplicate != null) {
                        duplicate.closeQuietly();
                    }
                    loadedCount++;
                } catch (RuntimeException ex) {
                    failedCount++;
                    if (properties.isFailFastOnStartup()) {
                        throw new IllegalStateException("Plugin preload failed. eqpId=" + eqpId + ", eqpKey=" + eqp.eqpKey(), ex);
                    }
                    log.warn("Plugin preload skipped due to validation/load failure. eqpId={}, eqpKey={}",
                            eqpId,
                            eqp.eqpKey(),
                            ex);
                }
            }

            replaceAllRuntimes(nextRuntimeMap);
            log.info("Plugin runtime preload completed. scannedEqpCount={}, loadedCount={}, noJarCount={}, failedCount={}, activeRuntimeCount={}",
                    scannedEqpCount,
                    loadedCount,
                    noJarCount,
                    failedCount,
                    nextRuntimeMap.size());
        } catch (RuntimeException ex) {
            closeRuntimeMapQuietly(nextRuntimeMap);
            throw ex;
        }
    }

    /**
     * loadAllEqps 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    private List<TcEqp> loadAllEqps() {
        final List<TcEqp> results = new ArrayList<>();
        final int pageSize = properties.getPageSize();
        int offset = 0;

        while (true) {
            final List<TcEqp> page = eqpStore.findAll(PageRequest.of(offset, pageSize));
            if (page == null || page.isEmpty()) {
                break;
            }
            results.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return results;
    }

    private PluginRuntime buildPluginRuntime(
            final String eqpId,
            final String jarFileName,
            final byte[] jarBytes
    ) {
        validateJarBytes(eqpId, jarBytes);
        final Path jarPath = writeJarToTemp(eqpId, jarFileName, jarBytes);
        final URLClassLoader classLoader = createClassLoader(jarPath);

        try {
            final DiscoveredExecutors executors = discoverExecutors(jarBytes, classLoader);

            final BusinessWorkflowActionRegistryBuilder builder = new BusinessWorkflowActionRegistryBuilder();
            if (executors.secsExecutor != null) {
                builder.registerExecutor(executors.secsExecutor, BusinessWorkflowActionMessageType.SECS);
            }
            if (executors.socketExecutor != null) {
                builder.registerExecutor(executors.socketExecutor, BusinessWorkflowActionMessageType.SOCKET);
            }
            if (executors.mesExecutor != null) {
                builder.registerExecutor(executors.mesExecutor, BusinessWorkflowActionMessageType.MES);
            }
            final BusinessWorkflowActionRegistry registry = builder.build();
            if (registry.size() == 0) {
                throw new IllegalStateException("No @TcAction methods found in plugin jar. eqpId=" + eqpId);
            }

            log.info("Plugin runtime validated. eqpId={}, jarFileName={}, actionCount={}, executors=[secs:{},socket:{},mes:{}]",
                    eqpId,
                    jarFileName,
                    registry.size(),
                    executors.secsExecutor != null,
                    executors.socketExecutor != null,
                    executors.mesExecutor != null);
            if (log.isDebugEnabled()) {
                log.debug("Plugin action keys. eqpId={}, keys={}", eqpId, registry.keys());
            }

            return new PluginRuntime(eqpId, jarFileName, jarPath, classLoader, registry);
        } catch (RuntimeException ex) {
            closeClassLoaderQuietly(classLoader, eqpId, jarFileName);
            deleteTempJarQuietly(jarPath);
            throw ex;
        }
    }

    /**
     * 플러그인 JAR 바이트의 최소 유효성을 검증합니다.
     *
     * <p>maxJarBytes 초과 시 로딩을 차단해 메모리 사용량 급증을 방지합니다.</p>
     */
    private void validateJarBytes(final String eqpId, final byte[] jarBytes) {
        if (jarBytes == null || jarBytes.length == 0) {
            throw new IllegalStateException("tc_jar_business.jar_file is empty. eqpId=" + eqpId);
        }

        final long maxJarBytes = properties.getMaxJarBytes();
        if (jarBytes.length > maxJarBytes) {
            throw new IllegalStateException("tc_jar_business.jar_file exceeds max size. eqpId="
                    + eqpId
                    + ", sizeBytes="
                    + jarBytes.length
                    + ", maxJarBytes="
                    + maxJarBytes);
        }

        if (log.isDebugEnabled()) {
            log.debug("Plugin jar bytes validated. eqpId={}, sizeBytes={}, maxJarBytes={}",
                    eqpId,
                    jarBytes.length,
                    maxJarBytes);
        }
    }

    private DiscoveredExecutors discoverExecutors(
            final byte[] jarBytes,
            final ClassLoader classLoader
    ) {
        final List<String> classNames = extractClassNames(jarBytes);
        if (log.isDebugEnabled()) {
            log.debug("Plugin class discovery completed. classCount={}", classNames.size());
        }

        SecsActionExecutor secsExecutor = null;
        SocketActionExecutor socketExecutor = null;
        MesActionExecutor mesExecutor = null;

        for (String className : classNames) {
            final Class<?> loadedClass;
            try {
                loadedClass = classLoader.loadClass(className);
            } catch (ClassNotFoundException | NoClassDefFoundError ex) {
                if (log.isDebugEnabled()) {
                    log.debug("Plugin class load skipped. className={}", className, ex);
                }
                continue;
            }

            if (loadedClass.isInterface() || Modifier.isAbstract(loadedClass.getModifiers())) {
                continue;
            }

            if (SecsActionExecutor.class.isAssignableFrom(loadedClass)) {
                if (secsExecutor != null) {
                    throw new IllegalStateException("Multiple SecsActionExecutor implementations found in plugin jar");
                }
                secsExecutor = instantiateExecutor(loadedClass, SecsActionExecutor.class);
                continue;
            }
            if (SocketActionExecutor.class.isAssignableFrom(loadedClass)) {
                if (socketExecutor != null) {
                    throw new IllegalStateException("Multiple SocketActionExecutor implementations found in plugin jar");
                }
                socketExecutor = instantiateExecutor(loadedClass, SocketActionExecutor.class);
                continue;
            }
            if (MesActionExecutor.class.isAssignableFrom(loadedClass)) {
                if (mesExecutor != null) {
                    throw new IllegalStateException("Multiple MesActionExecutor implementations found in plugin jar");
                }
                mesExecutor = instantiateExecutor(loadedClass, MesActionExecutor.class);
            }
        }

        return new DiscoveredExecutors(secsExecutor, socketExecutor, mesExecutor);
    }

    /**
     * instantiateExecutor 기능을 수행합니다.
     *
     * @param rawClass 입력 값
     * @param expectedType 입력 값
     * @return 처리 결과
     */

    private <T> T instantiateExecutor(final Class<?> rawClass, final Class<T> expectedType) {
        try {
            final Constructor<?> constructor = rawClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            final Object instance = constructor.newInstance();
            return expectedType.cast(instance);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to instantiate plugin executor class: " + rawClass.getName(), ex);
        }
    }

    /**
     * swapRuntime 기능을 수행합니다.
     *
     * @param eqpId 입력 값
     * @param newRuntime 입력 값
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

        log.info("Plugin runtime swapped. eqpId={}, jarFileName={}, actionCount={}",
                eqpId,
                newRuntime.jarFileName(),
                newRuntime.registry().size());
    }

    /**
     * 특정 eqpId의 플러그인 런타임을 제거합니다.
     *
     * <p>JAR 삭제/미존재 시 호출되며, 이후 액션 해석은 core fallback으로 동작합니다.</p>
     */
    private void removeRuntimeByEqpId(final String eqpId, final String reason) {
        PluginRuntime removedRuntime;
        while (true) {
            final Map<String, PluginRuntime> current = runtimeByEqpIdRef.get();
            final Map<String, PluginRuntime> next = new LinkedHashMap<>(current);
            removedRuntime = next.remove(eqpId);
            if (runtimeByEqpIdRef.compareAndSet(current, Map.copyOf(next))) {
                break;
            }
        }

        if (removedRuntime != null) {
            removedRuntime.closeQuietly();
        }

        logReloadApplied(
                eqpId,
                "REMOVE",
                removedRuntime != null,
                removedRuntime == null ? null : removedRuntime.jarFileName(),
                removedRuntime == null ? 0 : removedRuntime.registry().size(),
                reason
        );
    }

    /**
     * replaceAllRuntimes 기능을 수행합니다.
     *
     * @param nextRuntimeMap 입력 값
     */

    private void replaceAllRuntimes(final Map<String, PluginRuntime> nextRuntimeMap) {
        final Map<String, PluginRuntime> previous = runtimeByEqpIdRef.getAndSet(Map.copyOf(nextRuntimeMap));
        closeRuntimeMapQuietly(previous);
    }

    /**
     * closeRuntimeMapQuietly 기능을 수행합니다.
     *
     * @param runtimeMap 입력 값
     */

    private static void closeRuntimeMapQuietly(final Map<String, PluginRuntime> runtimeMap) {
        if (runtimeMap == null || runtimeMap.isEmpty()) {
            return;
        }
        for (PluginRuntime runtime : runtimeMap.values()) {
            if (runtime != null) {
                runtime.closeQuietly();
            }
        }
    }

    /**
     * extractClassNames 기능을 수행합니다.
     *
     * @param jarBytes 입력 값
     * @return 처리 결과
     */

    private static List<String> extractClassNames(final byte[] jarBytes) {
        final List<String> classNames = new ArrayList<>();
        try (JarInputStream inputStream = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            JarEntry entry;
            while ((entry = inputStream.getNextJarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                final String className = entry.getName()
                        .substring(0, entry.getName().length() - ".class".length())
                        .replace('/', '.');
                classNames.add(className);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read plugin jar entries", ex);
        }
        return classNames;
    }

    private static Path writeJarToTemp(
            final String eqpId,
            final String jarFileName,
            final byte[] jarBytes
    ) {
        try {
            Files.createDirectories(PLUGIN_TEMP_ROOT);
            final String rawPrefix = sanitizeFileToken(eqpId) + "-";
            final String prefix = rawPrefix.length() >= 3 ? rawPrefix : "eqp-";
            final Path jarPath = Files.createTempFile(
                    PLUGIN_TEMP_ROOT,
                    prefix,
                    "-" + sanitizeFileToken(jarFileName)
            );
            Files.write(jarPath, jarBytes);
            return jarPath;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write plugin jar to temp file. eqpId=" + eqpId, ex);
        }
    }

    /**
     * createClassLoader 기능을 수행합니다.
     *
     * @param jarPath 입력 값
     * @return 처리 결과
     */

    private static URLClassLoader createClassLoader(final Path jarPath) {
        try {
            final URL[] urls = new URL[]{jarPath.toUri().toURL()};
            return new URLClassLoader(urls, BusinessWorkflowPluginRuntimeManager.class.getClassLoader());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create plugin class loader. jarPath=" + jarPath, ex);
        }
    }

    /**
     * normalizeEqpId 기능을 수행합니다.
     *
     * @param eqpId 입력 값
     * @return 처리 결과
     */

    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null) {
            return null;
        }
        final String normalized = eqpId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * normalizeJarFileName 기능을 수행합니다.
     *
     * @param jarFileName 입력 값
     * @return 처리 결과
     */

    private static String normalizeJarFileName(final String jarFileName) {
        if (jarFileName == null || jarFileName.isBlank()) {
            return DEFAULT_PLUGIN_JAR_FILE_NAME;
        }
        return jarFileName.trim();
    }

    /**
     * sanitizeFileToken 기능을 수행합니다.
     *
     * @param value 입력 값
     * @return 처리 결과
     */

    private static String sanitizeFileToken(final String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void closeClassLoaderQuietly(
            final URLClassLoader classLoader,
            final String eqpId,
            final String jarFileName
    ) {
        try {
            classLoader.close();
        } catch (Exception ex) {
            log.warn("Plugin classloader close failed. eqpId={}, jarFileName={}", eqpId, jarFileName, ex);
        }
    }

    /**
     * deleteTempJarQuietly 기능을 수행합니다.
     *
     * @param jarPath 입력 값
     */

    private static void deleteTempJarQuietly(final Path jarPath) {
        try {
            Files.deleteIfExists(jarPath);
        } catch (Exception ex) {
            log.warn("Temporary plugin jar deletion failed. path={}", jarPath, ex);
        }
    }

    /**
     * 플러그인 리로드 시작 로그를 표준 포맷으로 기록합니다.
     */
    private void logReloadStarted(
            final String eqpId,
            final long eqpKey,
            final boolean hadPreviousRuntime
    ) {
        log.info("{}. eqpId={}, eqpKey={}, hadPreviousRuntime={}",
                RELOAD_EVENT_STARTED,
                eqpId,
                eqpKey,
                hadPreviousRuntime);
    }

    /**
     * 플러그인 리로드 적용 로그를 표준 포맷으로 기록합니다.
     */
    private void logReloadApplied(
            final String eqpId,
            final String operation,
            final boolean changed,
            final String jarFileName,
            final int actionCount,
            final String reason
    ) {
        if (changed) {
            log.info("{}. eqpId={}, operation={}, changed={}, jarFileName={}, actionCount={}, reason={}",
                    RELOAD_EVENT_APPLIED,
                    eqpId,
                    operation,
                    true,
                    jarFileName == null ? "N/A" : jarFileName,
                    actionCount,
                    reason == null ? "N/A" : reason);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("{}. eqpId={}, operation={}, changed={}, jarFileName={}, actionCount={}, reason={}",
                    RELOAD_EVENT_APPLIED,
                    eqpId,
                    operation,
                    false,
                    jarFileName == null ? "N/A" : jarFileName,
                    actionCount,
                    reason == null ? "N/A" : reason);
        }
    }

    /**
     * 플러그인 리로드 실패/롤백 로그를 표준 포맷으로 기록합니다.
     */
    private void logReloadRolledBack(
            final String eqpId,
            final boolean runtimePreserved,
            final Throwable cause
    ) {
        log.warn("{}. eqpId={}, runtimePreserved={}, reason={}",
                RELOAD_EVENT_ROLLED_BACK,
                eqpId,
                runtimePreserved,
                cause == null ? "N/A" : cause.getMessage(),
                cause);
    }

    /**
     * 로딩된 플러그인 런타임 컨테이너입니다.
     */
    private record PluginRuntime(
            String eqpId,
            String jarFileName,
            Path jarPath,
            URLClassLoader classLoader,
            BusinessWorkflowActionRegistry registry
    ) {

        /**
         * 런타임 리소스를 정리합니다.
         */
        private void closeQuietly() {
            try {
                classLoader.close();
            } catch (Exception ex) {
                log.warn("Plugin classloader close failed. eqpId={}, jarFileName={}", eqpId, jarFileName, ex);
            }
            try {
                Files.deleteIfExists(jarPath);
            } catch (Exception ex) {
                log.warn("Plugin jar temp file delete failed. eqpId={}, path={}", eqpId, jarPath, ex);
            }
        }
    }

    /**
     * 플러그인에서 탐지된 Executor 묶음입니다.
     */
    private static final class DiscoveredExecutors {
        private final SecsActionExecutor secsExecutor;
        private final SocketActionExecutor socketExecutor;
        private final MesActionExecutor mesExecutor;

        private DiscoveredExecutors(
                final SecsActionExecutor secsExecutor,
                final SocketActionExecutor socketExecutor,
                final MesActionExecutor mesExecutor
        ) {
            this.secsExecutor = secsExecutor;
            this.socketExecutor = socketExecutor;
            this.mesExecutor = mesExecutor;
        }
    }
}
