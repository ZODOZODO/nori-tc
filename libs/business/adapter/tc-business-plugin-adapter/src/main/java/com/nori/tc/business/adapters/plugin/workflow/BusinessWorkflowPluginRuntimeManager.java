package com.nori.tc.business.adapters.plugin.workflow;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistryBuilder;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeMutationPort;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeProvider;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractMesActionExecutor;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractSecsActionExecutor;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractSocketActionExecutor;
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
 * ?ㅻ퉬蹂??뚮윭洹몄씤 ?고???愿由ъ옄?낅땲??
 *
 * <p>??븷:</p>
 * <p>1) tc_jar_business?먯꽌 JAR 諛붿씠?덈━瑜??쎌뼱 ClassLoader濡?濡쒕뵫</p>
 * <p>2) Executor(@TcAction) ?ㅼ틪 ???≪뀡 ?덉??ㅽ듃由?援ъ꽦</p>
 * <p>3) 寃利??깃났 ??eqpId 湲곗??쇰줈 plugin runtime ?먯옄??atomic) ?ㅼ솑</p>
 *
 * <p>Step 14 ?뺤옣:</p>
 * <p>- 湲곕룞 ??plugin runtime preload 吏??/p>
 * <p>- preload fail-fast ?뺤콉/?섏씠吏 議고쉶 ?ш린 ?ㅼ젙 吏??/p>
 */
@Component
public class BusinessWorkflowPluginRuntimeManager
        implements BusinessWorkflowPluginRuntimeProvider, BusinessWorkflowPluginRuntimeMutationPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowPluginRuntimeManager.class);

    /**
     * 由щ줈???쒖옉 ?쒖? 濡쒓렇 ?대깽?몃챸?낅땲??
     */
    private static final String RELOAD_EVENT_STARTED = "PLUGIN_RELOAD_STARTED";

    /**
     * 由щ줈???곸슜 ?꾨즺 ?쒖? 濡쒓렇 ?대깽?몃챸?낅땲??
     */
    private static final String RELOAD_EVENT_APPLIED = "PLUGIN_RELOAD_APPLIED";

    /**
     * 由щ줈??濡ㅻ갚 ?쒖? 濡쒓렇 ?대깽?몃챸?낅땲??
     */
    private static final String RELOAD_EVENT_ROLLED_BACK = "PLUGIN_RELOAD_ROLLED_BACK";

    /**
     * jar ?뚯씪紐낆씠 鍮꾩뼱 ?덉쓣 ???ъ슜??湲곕낯紐낆엯?덈떎.
     */
    private static final String DEFAULT_PLUGIN_JAR_FILE_NAME = "workflow-plugin.jar";

    /**
     * ?뚮윭洹몄씤 JAR ?꾩떆 ?뚯씪????ν븷 猷⑦듃 ?붾젆?곕━?낅땲??
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
     * eqpId -> PluginRuntime 留ㅽ븨 ?ㅻ깄?룹엯?덈떎.
     *
     * <p>??긽 遺덈? 留?Map.copyOf)?쇰줈 ??ν븯硫? 媛깆떊? ?먯옄?곸쑝濡?援먯껜?⑸땲??</p>
     */
    private final AtomicReference<Map<String, PluginRuntime>> runtimeByEqpIdRef = new AtomicReference<>(Map.of());

    /**
     * ?뚮윭洹몄씤 ?고???愿由ъ옄 ?섏〈?깆쓣 二쇱엯諛쏆뒿?덈떎.
     *
     * @param eqpStore eqp 議고쉶 store
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
     * ?좏뵆由ъ??댁뀡 湲곕룞 吏곹썑 preload ?뺤콉???섑뻾?⑸땲??
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
     * ?좏뵆由ъ??댁뀡 醫낅즺 ???뚮윭洹몄씤 ?고???由ъ냼?ㅻ? ?뺣━?⑸땲??
     *
     * <p>URLClassLoader/?꾩떆 JAR ?뚯씪???꾩쟻?섏? ?딅룄濡? 醫낅즺 ?쒖젏??     * ?꾩껜 ?고????ㅻ깄?룹쓣 鍮꾩슦怨??먯썝???レ뒿?덈떎.</p>
     */
    @PreDestroy
    public void shutdown() {
        final Map<String, PluginRuntime> previous = runtimeByEqpIdRef.getAndSet(Map.of());
        closeRuntimeMapQuietly(previous);
        log.info("Business workflow plugin runtime manager shutdown completed. closedRuntimeCount={}",
                previous.size());
    }

    /**
     * findRegistryByEqpId 湲곕뒫???섑뻾?⑸땲??
     *
     * @param eqpId ?낅젰 媛?     * @return 泥섎━ 寃곌낵
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
     * reloadByEqpId 湲곕뒫???섑뻾?⑸땲??
     *
     * @param eqpId ?낅젰 媛?     */

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
             * Phase 3 "?≪뀡 ??젣 fallback" 洹쒖튃:
             * - JAR ?됱씠 ??젣?섏뿀嫄곕굹 ?뚯씪??鍮꾩뼱 ?덉쑝硫??뚮윭洹몄씤 ?고??꾩쓣 ?쒓굅?⑸땲??
             * - ?댄썑 ?ㅽ뻾湲곕뒗 core registry留??ъ슜?섍쾶 ?섏뼱 ?먮룞 fallback ?⑸땲??
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
             * build/swap ?덉쇅 ??湲곗〈 runtime? ?좎??⑸땲??
             * (swap ?댁쟾 ?ㅽ뙣, ?먮뒗 CAS 援먯껜 ?ㅽ뙣 ?ъ떆??以??덉쇅 ?놁쓬)
             */
            final boolean runtimePreserved = runtimeByEqpIdRef.get().containsKey(normalizedEqpId);
            logReloadRolledBack(normalizedEqpId, runtimePreserved, ex);
            throw ex;
        }
    }

    /**
     * ?뱀젙 eqpId???뚮윭洹몄씤 ?고??꾩쓣 ?쒓굅?⑸땲??
     *
     * <p>UI EQP_DELETE 泥섎━ 寃쎈줈?먯꽌 ?몄텧?섎ŉ,
     * ?대? ?고??꾩씠 ?녿뒗 寃쎌슦?먮뒗 ?덉쇅 ?놁씠 臾댁떆?⑸땲??</p>
     *
     * @param eqpId ?쒓굅 ????ㅻ퉬 ID
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
     * DB 湲곗??쇰줈 ?꾩껜 ?뚮윭洹몄씤 ?고??꾩쓣 preload/?ъ“由쏀빀?덈떎.
     *
     * <p>寃利??깃났??eqp留????ㅻ깄?룹뿉 ?ы븿?섎ŉ, 理쒖쥌?곸쑝濡?湲곗〈 ?ㅻ깄?룰낵 ?먯옄?곸쑝濡?援먯껜?⑸땲??</p>
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
     * loadAllEqps 湲곕뒫???섑뻾?⑸땲??
     *
     * @return 泥섎━ 寃곌낵
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
     * ?뚮윭洹몄씤 JAR 諛붿씠?몄쓽 理쒖냼 ?좏슚?깆쓣 寃利앺빀?덈떎.
     *
     * <p>maxJarBytes 珥덇낵 ??濡쒕뵫??李⑤떒??硫붾え由??ъ슜??湲됱쬆??諛⑹??⑸땲??</p>
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
     * instantiateExecutor 湲곕뒫???섑뻾?⑸땲??
     *
     * @param rawClass ?낅젰 媛?     * @param expectedType ?낅젰 媛?     * @return 泥섎━ 寃곌낵
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
     * swapRuntime 湲곕뒫???섑뻾?⑸땲??
     *
     * @param eqpId ?낅젰 媛?     * @param newRuntime ?낅젰 媛?     */

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
     * ?뱀젙 eqpId???뚮윭洹몄씤 ?고??꾩쓣 ?쒓굅?⑸땲??
     *
     * <p>JAR ??젣/誘몄〈?????몄텧?섎ŉ, ?댄썑 ?≪뀡 ?댁꽍? core fallback?쇰줈 ?숈옉?⑸땲??</p>
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
     * replaceAllRuntimes 湲곕뒫???섑뻾?⑸땲??
     *
     * @param nextRuntimeMap ?낅젰 媛?     */

    private void replaceAllRuntimes(final Map<String, PluginRuntime> nextRuntimeMap) {
        final Map<String, PluginRuntime> previous = runtimeByEqpIdRef.getAndSet(Map.copyOf(nextRuntimeMap));
        closeRuntimeMapQuietly(previous);
    }

    /**
     * closeRuntimeMapQuietly 湲곕뒫???섑뻾?⑸땲??
     *
     * @param runtimeMap ?낅젰 媛?     */

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
     * extractClassNames 湲곕뒫???섑뻾?⑸땲??
     *
     * @param jarBytes ?낅젰 媛?     * @return 泥섎━ 寃곌낵
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
     * createClassLoader 湲곕뒫???섑뻾?⑸땲??
     *
     * @param jarPath ?낅젰 媛?     * @return 泥섎━ 寃곌낵
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
     * normalizeEqpId 湲곕뒫???섑뻾?⑸땲??
     *
     * @param eqpId ?낅젰 媛?     * @return 泥섎━ 寃곌낵
     */

    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null) {
            return null;
        }
        final String normalized = eqpId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * normalizeJarFileName 湲곕뒫???섑뻾?⑸땲??
     *
     * @param jarFileName ?낅젰 媛?     * @return 泥섎━ 寃곌낵
     */

    private static String normalizeJarFileName(final String jarFileName) {
        if (jarFileName == null || jarFileName.isBlank()) {
            return DEFAULT_PLUGIN_JAR_FILE_NAME;
        }
        return jarFileName.trim();
    }

    /**
     * sanitizeFileToken 湲곕뒫???섑뻾?⑸땲??
     *
     * @param value ?낅젰 媛?     * @return 泥섎━ 寃곌낵
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
     * deleteTempJarQuietly 湲곕뒫???섑뻾?⑸땲??
     *
     * @param jarPath ?낅젰 媛?     */

    private static void deleteTempJarQuietly(final Path jarPath) {
        try {
            Files.deleteIfExists(jarPath);
        } catch (Exception ex) {
            log.warn("Temporary plugin jar deletion failed. path={}", jarPath, ex);
        }
    }

    /**
     * ?뚮윭洹몄씤 由щ줈???쒖옉 濡쒓렇瑜??쒖? ?щ㎎?쇰줈 湲곕줉?⑸땲??
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
     * ?뚮윭洹몄씤 由щ줈???곸슜 濡쒓렇瑜??쒖? ?щ㎎?쇰줈 湲곕줉?⑸땲??
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
     * ?뚮윭洹몄씤 由щ줈???ㅽ뙣/濡ㅻ갚 濡쒓렇瑜??쒖? ?щ㎎?쇰줈 湲곕줉?⑸땲??
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
     * 濡쒕뵫???뚮윭洹몄씤 ?고???而⑦뀒?대꼫?낅땲??
     */
    private record PluginRuntime(
            String eqpId,
            String jarFileName,
            Path jarPath,
            URLClassLoader classLoader,
            BusinessWorkflowActionRegistry registry
    ) {

        /**
         * ?고???由ъ냼?ㅻ? ?뺣━?⑸땲??
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
     * ?뚮윭洹몄씤?먯꽌 ?먯???Executor 臾띠쓬?낅땲??
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


