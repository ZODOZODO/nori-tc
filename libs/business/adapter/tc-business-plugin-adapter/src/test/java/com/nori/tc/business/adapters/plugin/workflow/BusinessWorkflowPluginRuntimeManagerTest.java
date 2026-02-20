package com.nori.tc.business.adapters.plugin.workflow;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistryBuilder;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractSocketActionExecutor;
import com.nori.tc.business.core.workflow.api.annotation.TcAction;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqp;
import com.nori.tc.db.core.jar.store.TcJarBusinessStore;
import com.nori.tc.db.core.jar.upsert.UpsertTcJarBusiness;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.jar.TcJarBusiness;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link BusinessWorkflowPluginRuntimeManager} Step14(preload ?뺤콉) ?⑥쐞 ?뚯뒪?몄엯?덈떎.
 */
class BusinessWorkflowPluginRuntimeManagerTest {

    @Test
    void shouldSkipPreloadWhenLoadOnStartupIsDisabled() {
        final FakeEqpStore eqpStore = new FakeEqpStore(List.of(
                createEqp(1L, "EQP-01", 101L)
        ));
        final FakeJarBusinessStore jarStore = new FakeJarBusinessStore(Map.of(
                1L, createInvalidJar(1L)
        ));

        final BusinessWorkflowPluginRuntimeProperties properties = createProperties(false, true, 100);
        final BusinessWorkflowPluginRuntimeManager manager =
                new BusinessWorkflowPluginRuntimeManager(eqpStore, jarStore, properties);

        manager.initialize();

        Assertions.assertEquals(0, eqpStore.findAllCallCount, "loadOnStartup=false?대㈃ DB 議고쉶瑜??섑뻾?섏? ?딆븘???⑸땲??");
        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isEmpty());
    }

    @Test
    void shouldContinueWhenPreloadFailsAndFailFastIsDisabled() {
        final FakeEqpStore eqpStore = new FakeEqpStore(List.of(
                createEqp(1L, "EQP-01", 101L)
        ));
        final FakeJarBusinessStore jarStore = new FakeJarBusinessStore(Map.of(
                1L, createInvalidJar(1L)
        ));

        final BusinessWorkflowPluginRuntimeProperties properties = createProperties(true, false, 100);
        final BusinessWorkflowPluginRuntimeManager manager =
                new BusinessWorkflowPluginRuntimeManager(eqpStore, jarStore, properties);

        manager.initialize();

        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isEmpty());
    }

    @Test
    void shouldThrowWhenPreloadFailsAndFailFastIsEnabled() {
        final FakeEqpStore eqpStore = new FakeEqpStore(List.of(
                createEqp(1L, "EQP-01", 101L)
        ));
        final FakeJarBusinessStore jarStore = new FakeJarBusinessStore(Map.of(
                1L, createInvalidJar(1L)
        ));

        final BusinessWorkflowPluginRuntimeProperties properties = createProperties(true, true, 100);
        final BusinessWorkflowPluginRuntimeManager manager =
                new BusinessWorkflowPluginRuntimeManager(eqpStore, jarStore, properties);

        Assertions.assertThrows(IllegalStateException.class, manager::initialize);
    }

    @Test
    void shouldRemoveExistingRuntimeWhenJarIsMissingOnReload() {
        final FakeEqpStore eqpStore = new FakeEqpStore(List.of(
                createEqp(1L, "EQP-01", 101L)
        ));
        final FakeJarBusinessStore jarStore = new FakeJarBusinessStore(Map.of());

        final BusinessWorkflowPluginRuntimeProperties properties = createProperties(false, true, 100);
        final BusinessWorkflowPluginRuntimeManager manager =
                new BusinessWorkflowPluginRuntimeManager(eqpStore, jarStore, properties);

        injectPluginRuntimeForTest(manager, "EQP-01");
        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isPresent(), "?ъ쟾 議곌굔: runtime??議댁옱?댁빞 ?⑸땲??");

        manager.reloadByEqpId("EQP-01");

        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isEmpty(), "JAR 誘몄〈????runtime???쒓굅?섏뼱???⑸땲??");
    }

    @Test
    void shouldPreserveExistingRuntimeWhenReloadValidationFails() {
        final FakeEqpStore eqpStore = new FakeEqpStore(List.of(
                createEqp(1L, "EQP-01", 101L)
        ));
        final FakeJarBusinessStore jarStore = new FakeJarBusinessStore(Map.of(
                1L, createInvalidJar(1L)
        ));

        final BusinessWorkflowPluginRuntimeProperties properties = createProperties(false, true, 100);
        final BusinessWorkflowPluginRuntimeManager manager =
                new BusinessWorkflowPluginRuntimeManager(eqpStore, jarStore, properties);

        injectPluginRuntimeForTest(manager, "EQP-01");
        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isPresent(), "?ъ쟾 議곌굔: runtime??議댁옱?댁빞 ?⑸땲??");

        Assertions.assertThrows(IllegalStateException.class, () -> manager.reloadByEqpId("EQP-01"));
        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isPresent(),
                "由щ줈???ㅽ뙣 ??湲곗〈 runtime? 濡ㅻ갚(?좎?)?섏뼱???⑸땲??");
    }

    /**
     * ?뚯뒪?몄슜 plugin runtime ?꾨줈?쇳떚瑜??앹꽦?⑸땲??
     */
    private static BusinessWorkflowPluginRuntimeProperties createProperties(
            final boolean loadOnStartup,
            final boolean failFastOnStartup,
            final int pageSize
    ) {
        final BusinessWorkflowPluginRuntimeProperties properties = new BusinessWorkflowPluginRuntimeProperties();
        properties.setLoadOnStartup(loadOnStartup);
        properties.setFailFastOnStartup(failFastOnStartup);
        properties.setPageSize(pageSize);
        properties.validate();
        return properties;
    }

    /**
     * ?뚯뒪?몃? ?꾪빐 manager ?대? runtime 留듭뿉 ?⑥씪 runtime??二쇱엯?⑸땲??
     *
     * <p>?ㅼ젣 由щ줈??寃쎈줈瑜?嫄곗튂?ㅻ㈃ ?좏슚??JAR ?앹꽦???꾩슂?섎?濡?
     * 蹂??뚯뒪?몄뿉?쒕뒗 reflection?쇰줈 理쒖냼 runtime留?二쇱엯??     * "??젣/濡ㅻ갚 ?뺤콉" ?먯껜瑜?寃利앺빀?덈떎.</p>
     */
    private static void injectPluginRuntimeForTest(
            final BusinessWorkflowPluginRuntimeManager manager,
            final String eqpId
    ) {
        try {
            final Class<?> pluginRuntimeClass = findPluginRuntimeClass();
            final Constructor<?> constructor = pluginRuntimeClass.getDeclaredConstructor(
                    String.class,
                    String.class,
                    Path.class,
                    URLClassLoader.class,
                    BusinessWorkflowActionRegistry.class
            );
            constructor.setAccessible(true);

            final Path tempJarPath = Files.createTempFile("business-plugin-runtime-test-", ".jar");
            final URLClassLoader classLoader = new URLClassLoader(new URL[0], BusinessWorkflowPluginRuntimeManager.class.getClassLoader());
            final BusinessWorkflowActionRegistry registry = createTestActionRegistry();
            final Object pluginRuntime = constructor.newInstance(
                    eqpId,
                    "test-plugin.jar",
                    tempJarPath,
                    classLoader,
                    registry
            );

            final Method swapMethod = BusinessWorkflowPluginRuntimeManager.class.getDeclaredMethod(
                    "swapRuntime",
                    String.class,
                    pluginRuntimeClass
            );
            swapMethod.setAccessible(true);
            swapMethod.invoke(manager, eqpId, pluginRuntime);
        } catch (Exception ex) {
            throw new IllegalStateException("?뚯뒪??runtime 二쇱엯???ㅽ뙣?덉뒿?덈떎.", ex);
        }
    }

    /**
     * ?뚯뒪?몄슜 理쒖냼 ?≪뀡 ?덉??ㅽ듃由щ? ?앹꽦?⑸땲??
     */
    private static BusinessWorkflowActionRegistry createTestActionRegistry() {
        final AbstractSocketActionExecutor executor = new AbstractSocketActionExecutor() {
            /**
             * execute 湲곕뒫???섑뻾?⑸땲??
             *
             * @param context ?낅젰 媛?             */

            @TcAction("TEST_ACTION")
            public void execute(final BusinessWorkflowActionContext context) {
                // no-op
            }
        };
        return new BusinessWorkflowActionRegistryBuilder()
                .registerExecutor(executor, BusinessWorkflowActionMessageType.SOCKET)
                .build();
    }

    /**
     * manager ?대? private record(PluginRuntime) ??낆쓣 議고쉶?⑸땲??
     */
    private static Class<?> findPluginRuntimeClass() {
        for (Class<?> nestedClass : BusinessWorkflowPluginRuntimeManager.class.getDeclaredClasses()) {
            if ("PluginRuntime".equals(nestedClass.getSimpleName())) {
                return nestedClass;
            }
        }
        throw new IllegalStateException("PluginRuntime nested class瑜?李얠? 紐삵뻽?듬땲??");
    }

    /**
     * ?뚯뒪?몄슜 eqp ?덉퐫?쒕? ?앹꽦?⑸땲??
     */
    private static TcEqp createEqp(final long eqpKey, final String eqpId, final long modelKey) {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcEqp(
                eqpKey,
                eqpId,
                ProtocolType.SOCKET,
                "127.0.0.1",
                5000,
                modelKey,
                true,
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }

    /**
     * ?뚯뒪?몄슜 invalid plugin jar ?덉퐫?쒕? ?앹꽦?⑸땲??
     */
    private static TcJarBusiness createInvalidJar(final long eqpKey) {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcJarBusiness(
                eqpKey,
                "invalid.jar",
                "INVALID_JAR_BYTES".getBytes(StandardCharsets.UTF_8),
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }

    /**
     * {@link TcEqpStore} ?뚯뒪???붾툝?낅땲??
     */
    private static final class FakeEqpStore implements TcEqpStore {
        private final List<TcEqp> eqps;
        private int findAllCallCount = 0;

        /**
         * FakeEqpStore ?앹꽦?먮? 珥덇린?뷀빀?덈떎.
         *
         * @param eqps ?낅젰 媛?         */

        private FakeEqpStore(final List<TcEqp> eqps) {
            this.eqps = new ArrayList<>(eqps);
        }

        /**
         * upsert 湲곕뒫???섑뻾?⑸땲??
         *
         * @param command ?낅젰 媛?         * @return 泥섎━ 寃곌낵
         */

        @Override
        public TcEqp upsert(final UpsertTcEqp command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findByEqpId 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpId ?낅젰 媛?         * @return 泥섎━ 寃곌낵
         */

        @Override
        public Optional<TcEqp> findByEqpId(final String eqpId) {
            return eqps.stream()
                    .filter(eqp -> eqpId != null && eqpId.equals(eqp.eqpId()))
                    .findFirst();
        }

        /**
         * findAll 湲곕뒫???섑뻾?⑸땲??
         *
         * @param page ?낅젰 媛?         * @return 泥섎━ 寃곌낵
         */

        @Override
        public List<TcEqp> findAll(final PageRequest page) {
            findAllCallCount++;
            final int from = Math.min(page.offset(), eqps.size());
            final int to = Math.min(from + page.limit(), eqps.size());
            return List.copyOf(eqps.subList(from, to));
        }

        /**
         * deleteByEqpId 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpId ?낅젰 媛?         */

        @Override
        public void deleteByEqpId(final String eqpId) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /**
     * {@link TcJarBusinessStore} ?뚯뒪???붾툝?낅땲??
     */
    private static final class FakeJarBusinessStore implements TcJarBusinessStore {
        private final Map<Long, TcJarBusiness> jarByEqpKey;

        /**
         * FakeJarBusinessStore ?앹꽦?먮? 珥덇린?뷀빀?덈떎.
         *
         * @param jarByEqpKey ?낅젰 媛?         */

        private FakeJarBusinessStore(final Map<Long, TcJarBusiness> jarByEqpKey) {
            this.jarByEqpKey = new LinkedHashMap<>(jarByEqpKey);
        }

        /**
         * upsert 湲곕뒫???섑뻾?⑸땲??
         *
         * @param command ?낅젰 媛?         * @return 泥섎━ 寃곌낵
         */

        @Override
        public TcJarBusiness upsert(final UpsertTcJarBusiness command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findByEqpKey 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpKey ?낅젰 媛?         * @return 泥섎━ 寃곌낵
         */

        @Override
        public Optional<TcJarBusiness> findByEqpKey(final long eqpKey) {
            return Optional.ofNullable(jarByEqpKey.get(eqpKey));
        }

        /**
         * deleteByEqpKey 湲곕뒫???섑뻾?⑸땲??
         *
         * @param eqpKey ?낅젰 媛?         */

        @Override
        public void deleteByEqpKey(final long eqpKey) {
            throw new UnsupportedOperationException("not used");
        }
    }
}




