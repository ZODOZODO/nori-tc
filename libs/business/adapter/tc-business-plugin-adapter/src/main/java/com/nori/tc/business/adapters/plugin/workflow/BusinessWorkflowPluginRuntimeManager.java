package com.nori.tc.business.adapters.plugin.workflow;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistryBuilder;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeProvider;
import com.nori.tc.business.action.AbstractMesActionExecutor;
import com.nori.tc.business.action.AbstractSecsActionExecutor;
import com.nori.tc.business.action.AbstractSocketActionExecutor;
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
 * 설비(`eqpId`)별 워크플로우 플러그인 런타임을 관리하는 매니저입니다.
 *
 * <p>주요 책임:</p>
 * <p>1) `tc_jar_business`에 저장된 JAR 바이트를 임시 파일로 기록하고 `ClassLoader`를 생성합니다.</p>
 * <p>2) 플러그인 JAR에서 Executor 구현체를 탐색하여 액션 레지스트리를 구성합니다.</p>
 * <p>3) `eqpId -> PluginRuntime` 맵을 CAS 방식으로 원자 교체하여 동시성 안전성을 보장합니다.</p>
 *
 * <p>운영 기능:</p>
 * <p>- 기동 시 preload</p>
 * <p>- 설비 단위 reload/remove</p>
 * <p>- 실패 시 기존 런타임 보존 및 상세 로깅</p>
 */
@Component
public class BusinessWorkflowPluginRuntimeManager
        implements BusinessWorkflowPluginRuntimeProvider, BusinessWorkflowPluginRuntimeMutationPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowPluginRuntimeManager.class);

    /**
     * 플러그인 리로드 시작 이벤트를 식별하기 위한 로그 상수입니다.
     */
    private static final String RELOAD_EVENT_STARTED = "PLUGIN_RELOAD_STARTED";

    /**
     * 플러그인 리로드 결과가 적용(업서트/삭제 포함)되었음을 나타내는 로그 상수입니다.
     */
    private static final String RELOAD_EVENT_APPLIED = "PLUGIN_RELOAD_APPLIED";

    /**
     * 플러그인 리로드 도중 예외가 발생하여 기존 상태를 유지한 경우 사용하는 로그 상수입니다.
     */
    private static final String RELOAD_EVENT_ROLLED_BACK = "PLUGIN_RELOAD_ROLLED_BACK";

    /**
     * DB에 저장된 JAR 파일명이 비어 있을 때 사용하는 기본 파일명입니다.
     */
    private static final String DEFAULT_PLUGIN_JAR_FILE_NAME = "workflow-plugin.jar";

    /**
     * 플러그인 JAR 임시 파일을 저장하는 루트 디렉터리입니다.
     *
     * <p>OS 임시 디렉터리 하위의 고정 경로를 사용하여 디버깅 및 정리 작업을 단순화합니다.</p>
     */
    private static final Path PLUGIN_TEMP_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"),
            "nori-tc",
            "business-plugin-runtime"
    );

    /**
     * 설비 메타데이터(`tc_eqp`)를 조회하는 저장소입니다.
     */
    private final TcEqpStore eqpStore;
    /**
     * 설비별 플러그인 JAR(`tc_jar_business`)을 조회하는 저장소입니다.
     */
    private final TcJarBusinessStore jarBusinessStore;
    /**
     * 플러그인 런타임 동작 정책 설정값입니다.
     */
    private final BusinessWorkflowPluginRuntimeProperties properties;

    /**
     * 현재 활성화된 플러그인 런타임 스냅샷(`eqpId -> PluginRuntime`)을 보관합니다.
     *
     * <p>읽기는 불변 맵을 그대로 조회하여 락 없이 수행하고,</p>
     * <p>쓰기는 복사본 생성 후 CAS(`compareAndSet`)로 교체하는 copy-on-write 전략을 사용합니다.</p>
     */
    private final AtomicReference<Map<String, PluginRuntime>> runtimeByEqpIdRef = new AtomicReference<>(Map.of());

    /**
     * 플러그인 런타임 매니저를 생성합니다.
     *
     * @param eqpStore 설비 메타데이터 조회 저장소
     * @param jarBusinessStore 설비별 플러그인 JAR 조회 저장소
     * @param properties 플러그인 런타임 정책 설정
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
     * 애플리케이션 기동 직후 플러그인 런타임 preload를 수행합니다.
     *
     * <p>`loadOnStartup=false`면 preload를 생략합니다.</p>
     * <p>preload 실패 시 `failFastOnStartup=true`이면 예외를 재전파하여 기동 실패로 처리합니다.</p>
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
     * 애플리케이션 종료 시 모든 플러그인 런타임 자원을 정리합니다.
     *
     * <p>런타임 맵을 먼저 빈 스냅샷으로 교체한 뒤 기존 `URLClassLoader`와 임시 JAR 파일을 정리합니다.</p>
     */
    @PreDestroy
    public void shutdown() {
        final Map<String, PluginRuntime> previous = runtimeByEqpIdRef.getAndSet(Map.of());
        closeRuntimeMapQuietly(previous);
        log.info("Business workflow plugin runtime manager shutdown completed. closedRuntimeCount={}",
                previous.size());
    }

    /**
     * 설비 ID에 해당하는 플러그인 액션 레지스트리를 조회합니다.
     *
     * <p>입력값을 trim 후 사용하며, 유효하지 않거나 런타임이 없으면 `Optional.empty()`를 반환합니다.</p>
     *
     * @param eqpId 설비 식별자
     * @return 플러그인 액션 레지스트리 조회 결과
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
     * 특정 설비의 플러그인 런타임을 다시 로드합니다.
     *
     * <p>JAR이 없으면 기존 플러그인 런타임을 제거하여 코어 레지스트리 fallback이 가능하도록 합니다.</p>
     * <p>JAR이 있으면 새 런타임을 검증/생성한 뒤 원자적으로 교체합니다.</p>
     *
     * @param eqpId 리로드 대상 설비 ID
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
             * JAR이 삭제되었거나 비어 있으면 기존 플러그인 런타임을 제거합니다.
             * 이후 호출부는 코어 레지스트리 fallback 경로를 사용하게 됩니다.
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
             * build/swap 중 예외가 발생한 경우 현재 맵 상태를 다시 확인하여
             * 기존 런타임이 유지되었는지 운영 로그로 남깁니다.
             */
            final boolean runtimePreserved = runtimeByEqpIdRef.get().containsKey(normalizedEqpId);
            logReloadRolledBack(normalizedEqpId, runtimePreserved, ex);
            throw ex;
        }
    }

    /**
     * 특정 설비의 플러그인 런타임을 강제로 제거합니다.
     *
     * <p>주로 UI의 설비 삭제/비활성화 이벤트와 연계되어 메모리 런타임을 즉시 제거할 때 사용합니다.</p>
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
     * DB에서 전체 설비를 조회하여 플러그인 런타임을 preload 합니다.
     *
     * <p>모든 런타임을 별도 맵에 먼저 구성한 후 마지막에 한 번에 교체하여 중간 상태 노출을 방지합니다.</p>
     * <p>`failFastOnStartup=false`이면 개별 설비 오류는 건너뛰고 나머지 설비 로딩을 계속합니다.</p>
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
                    // 동일 eqpId 중복 데이터가 있으면 마지막 값을 채택하고 이전 런타임은 즉시 정리합니다.
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
            // preload 전체 실패 시 이번 라운드에서 생성한 런타임 자원을 정리합니다.
            closeRuntimeMapQuietly(nextRuntimeMap);
            throw ex;
        }
    }

    /**
     * `tc_eqp` 전체 목록을 페이지 단위로 순차 조회합니다.
     *
     * <p>offset 기반 페이지네이션을 사용하며, 마지막 페이지(`size < pageSize`)를 만나면 종료합니다.</p>
     *
     * @return 조회된 전체 설비 목록
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
                // 마지막 페이지이므로 추가 조회 없이 종료합니다.
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return results;
    }

    /**
     * 플러그인 JAR 바이트로부터 실행 가능한 런타임 스냅샷을 생성합니다.
     *
     * <p>검증 -> 임시 파일 저장 -> classloader 생성 -> Executor 탐색 -> 레지스트리 빌드 순서로 진행합니다.</p>
     * <p>중간 단계 실패 시 생성된 자원(classloader/임시 파일)을 즉시 정리합니다.</p>
     *
     * @param eqpId 대상 설비 ID
     * @param jarFileName 임시 파일명 생성에 사용할 JAR 파일명
     * @param jarBytes 플러그인 JAR 바이트
     * @return 생성된 플러그인 런타임 스냅샷
     */
    private PluginRuntime buildPluginRuntime(
            final String eqpId,
            final String jarFileName,
            final byte[] jarBytes
    ) {
        validateJarBytes(eqpId, jarBytes);
        final Path jarPath = writeJarToTemp(eqpId, jarFileName, jarBytes);
        final URLClassLoader classLoader = createClassLoader(jarPath);

        try {
            // 플러그인 JAR 내부에서 메시지 타입별 Executor 구현체를 탐색합니다.
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
            // 부분 생성된 자원을 정리하여 임시 파일/클래스로더 누수를 방지합니다.
            closeClassLoaderQuietly(classLoader, eqpId, jarFileName);
            deleteTempJarQuietly(jarPath);
            throw ex;
        }
    }

    /**
     * 플러그인 JAR 바이트의 기본 유효성을 검증합니다.
     *
     * <p>빈 파일 여부와 최대 허용 크기(`maxJarBytes`)를 검사하여 과도한 메모리/디스크 사용을 방지합니다.</p>
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

    /**
     * 플러그인 JAR에서 지원 가능한 Executor 구현체(SECS/SOCKET/MES)를 탐색합니다.
     *
     * <p>추상 클래스/인터페이스는 제외하며, 타입별 구현체가 2개 이상 발견되면 예외를 발생시킵니다.</p>
     *
     * @param jarBytes 플러그인 JAR 바이트
     * @param classLoader 플러그인 전용 classloader
     * @return 발견된 Executor 묶음
     */
    private DiscoveredExecutors discoverExecutors(
            final byte[] jarBytes,
            final ClassLoader classLoader
    ) {
        final List<String> classNames = extractClassNames(jarBytes);
        if (log.isDebugEnabled()) {
            log.debug("Plugin class discovery completed. classCount={}", classNames.size());
        }

        AbstractSecsActionExecutor secsExecutor = null;
        AbstractSocketActionExecutor socketExecutor = null;
        AbstractMesActionExecutor mesExecutor = null;

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

            if (AbstractSecsActionExecutor.class.isAssignableFrom(loadedClass)) {
                if (secsExecutor != null) {
                    throw new IllegalStateException("Multiple AbstractSecsActionExecutor implementations found in plugin jar");
                }
                secsExecutor = instantiateExecutor(loadedClass, AbstractSecsActionExecutor.class);
                continue;
            }
            if (AbstractSocketActionExecutor.class.isAssignableFrom(loadedClass)) {
                if (socketExecutor != null) {
                    throw new IllegalStateException("Multiple AbstractSocketActionExecutor implementations found in plugin jar");
                }
                socketExecutor = instantiateExecutor(loadedClass, AbstractSocketActionExecutor.class);
                continue;
            }
            if (AbstractMesActionExecutor.class.isAssignableFrom(loadedClass)) {
                if (mesExecutor != null) {
                    throw new IllegalStateException("Multiple AbstractMesActionExecutor implementations found in plugin jar");
                }
                mesExecutor = instantiateExecutor(loadedClass, AbstractMesActionExecutor.class);
            }
        }

        return new DiscoveredExecutors(secsExecutor, socketExecutor, mesExecutor);
    }

    /**
     * 플러그인 Executor 구현체를 기본 생성자로 인스턴스화합니다.
     *
     * <p>기본 생성자가 없거나 접근/생성 중 예외가 발생하면 `IllegalStateException`으로 변환합니다.</p>
     *
     * @param rawClass 로딩된 실제 클래스
     * @param expectedType 기대하는 상위 타입
     * @param <T> 반환 타입
     * @return 생성된 Executor 인스턴스
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
     * 설비별 플러그인 런타임을 원자적으로 교체합니다.
     *
     * <p>불변 맵 복사본을 만든 뒤 CAS로 교체하며, 교체 성공 후 이전 런타임 자원을 정리합니다.</p>
     *
     * @param eqpId 대상 설비 ID
     * @param newRuntime 새로 생성된 런타임
     */

    private void swapRuntime(final String eqpId, final PluginRuntime newRuntime) {
        PluginRuntime previousRuntime;
        while (true) {
            final Map<String, PluginRuntime> current = runtimeByEqpIdRef.get();
            final Map<String, PluginRuntime> next = new LinkedHashMap<>(current);
            // copy-on-write 스냅샷에 새 런타임을 반영한 뒤 CAS로 원자 교체를 시도합니다.
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
     * 설비별 플러그인 런타임을 원자적으로 제거합니다.
     *
     * <p>런타임 제거 후 classloader/임시 파일을 정리하고, 변경 여부 및 사유를 표준 로그 포맷으로 남깁니다.</p>
     *
     * @param eqpId 대상 설비 ID
     * @param reason 제거 사유(운영/디버깅 로그용)
     */
    private void removeRuntimeByEqpId(final String eqpId, final String reason) {
        PluginRuntime removedRuntime;
        while (true) {
            final Map<String, PluginRuntime> current = runtimeByEqpIdRef.get();
            final Map<String, PluginRuntime> next = new LinkedHashMap<>(current);
            // copy-on-write 스냅샷에서 대상 설비 런타임을 제거한 뒤 CAS로 원자 교체를 시도합니다.
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
     * 전체 런타임 스냅샷을 새 맵으로 교체합니다.
     *
     * <p>preload 완료 시점에만 호출되며, 교체 후 이전 스냅샷의 자원을 일괄 정리합니다.</p>
     *
     * @param nextRuntimeMap 새 런타임 스냅샷
     */

    private void replaceAllRuntimes(final Map<String, PluginRuntime> nextRuntimeMap) {
        final Map<String, PluginRuntime> previous = runtimeByEqpIdRef.getAndSet(Map.copyOf(nextRuntimeMap));
        closeRuntimeMapQuietly(previous);
    }

    /**
     * 런타임 맵에 포함된 모든 런타임 자원을 안전하게 정리합니다.
     *
     * <p>개별 정리 실패는 각 런타임 내부에서 warn 로그로 처리합니다.</p>
     *
     * @param runtimeMap 정리 대상 런타임 맵
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
     * JAR 바이트에서 `.class` 엔트리의 FQCN 목록을 추출합니다.
     *
     * <p>클래스 로딩 전 단계에서 후보 목록을 만들기 위한 용도이며, 실제 로딩 실패 여부는 별도로 처리합니다.</p>
     *
     * @param jarBytes 플러그인 JAR 바이트
     * @return 클래스명 목록(FQCN)
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

    /**
     * 플러그인 JAR 바이트를 임시 디렉터리에 파일로 기록합니다.
     *
     * <p>설비 ID와 파일명을 이용해 사람이 추적 가능한 임시 파일명을 생성합니다.</p>
     *
     * @param eqpId 대상 설비 ID
     * @param jarFileName 원본 JAR 파일명
     * @param jarBytes 기록할 JAR 바이트
     * @return 생성된 임시 JAR 경로
     */
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
     * 임시 JAR 파일 경로를 기반으로 플러그인 전용 `URLClassLoader`를 생성합니다.
     *
     * @param jarPath 임시 저장된 플러그인 JAR 경로
     * @return 생성된 `URLClassLoader`
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
     * 설비 ID 문자열을 정규화합니다.
     *
     * <p>`null`은 그대로 `null`을 반환하고, trim 결과가 빈 문자열이면 `null`로 간주합니다.</p>
     *
     * @param eqpId 입력 설비 ID
     * @return 정규화된 설비 ID 또는 `null`
     */

    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null) {
            return null;
        }
        final String normalized = eqpId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * JAR 파일명을 정규화합니다.
     *
     * <p>비어 있으면 기본 파일명을 반환하고, 그렇지 않으면 trim 결과를 반환합니다.</p>
     *
     * @param jarFileName DB에서 조회한 JAR 파일명
     * @return 정규화된 JAR 파일명
     */

    private static String normalizeJarFileName(final String jarFileName) {
        if (jarFileName == null || jarFileName.isBlank()) {
            return DEFAULT_PLUGIN_JAR_FILE_NAME;
        }
        return jarFileName.trim();
    }

    /**
     * 임시 파일명에 사용할 문자열 토큰을 안전한 문자만 남기도록 정규화합니다.
     *
     * <p>영문/숫자/점/밑줄/하이픈 외 문자는 `_`로 치환합니다.</p>
     *
     * @param value 원본 문자열
     * @return 파일명 안전 토큰
     */

    private static String sanitizeFileToken(final String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * 플러그인 전용 classloader를 안전하게 종료합니다.
     *
     * <p>종료 실패는 런타임 상태 정리 과정 전체를 중단시키지 않도록 warn 로그만 남깁니다.</p>
     *
     * @param classLoader 종료 대상 classloader
     * @param eqpId 설비 ID(로그용)
     * @param jarFileName JAR 파일명(로그용)
     */
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
     * 임시 플러그인 JAR 파일을 조용히 삭제합니다.
     *
     * <p>삭제 실패는 런타임 동작 전체를 중단시키지 않도록 warn 로그만 남깁니다.</p>
     *
     * @param jarPath 삭제 대상 임시 JAR 경로
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
     * 플러그인 리로드 적용 결과(업서트/삭제)를 표준 포맷으로 기록합니다.
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
     * 플러그인 리로드 실패 시 기존 상태 유지 여부와 원인을 표준 포맷으로 기록합니다.
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
     * 설비별 플러그인 런타임 스냅샷입니다.
     *
     * <p>레지스트리와 함께 임시 JAR 경로 및 전용 classloader를 보관하여 교체/삭제 시 자원 정리가 가능하도록 합니다.</p>
     */
    private record PluginRuntime(
            String eqpId,
            String jarFileName,
            Path jarPath,
            URLClassLoader classLoader,
            BusinessWorkflowActionRegistry registry
    ) {

        /**
         * 플러그인 런타임이 보유한 자원(classloader, 임시 JAR 파일)을 안전하게 정리합니다.
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
     * 플러그인 JAR에서 발견한 Executor 구현체 묶음입니다.
     *
     * <p>메시지 타입별로 최대 1개만 허용되며, 미구현 타입은 `null`일 수 있습니다.</p>
     */
    private static final class DiscoveredExecutors {
        private final AbstractSecsActionExecutor secsExecutor;
        private final AbstractSocketActionExecutor socketExecutor;
        private final AbstractMesActionExecutor mesExecutor;

        private DiscoveredExecutors(
                final AbstractSecsActionExecutor secsExecutor,
                final AbstractSocketActionExecutor socketExecutor,
                final AbstractMesActionExecutor mesExecutor
        ) {
            this.secsExecutor = secsExecutor;
            this.socketExecutor = socketExecutor;
            this.mesExecutor = mesExecutor;
        }
    }
}
