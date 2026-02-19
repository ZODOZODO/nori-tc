package com.nori.tc.business.adapters.plugin.workflow;

import com.nori.tc.business.core.workflow.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.BusinessWorkflowActionRegistryBuilder;
import com.nori.tc.business.core.workflow.SocketActionExecutor;
import com.nori.tc.business.core.workflow.TcAction;
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
 * {@link BusinessWorkflowPluginRuntimeManager} Step14(preload 정책) 단위 테스트입니다.
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

        Assertions.assertEquals(0, eqpStore.findAllCallCount, "loadOnStartup=false이면 DB 조회를 수행하지 않아야 합니다.");
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
        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isPresent(), "사전 조건: runtime이 존재해야 합니다.");

        manager.reloadByEqpId("EQP-01");

        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isEmpty(), "JAR 미존재 시 runtime이 제거되어야 합니다.");
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
        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isPresent(), "사전 조건: runtime이 존재해야 합니다.");

        Assertions.assertThrows(IllegalStateException.class, () -> manager.reloadByEqpId("EQP-01"));
        Assertions.assertTrue(manager.findRegistryByEqpId("EQP-01").isPresent(),
                "리로드 실패 시 기존 runtime은 롤백(유지)되어야 합니다.");
    }

    /**
     * 테스트용 plugin runtime 프로퍼티를 생성합니다.
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
     * 테스트를 위해 manager 내부 runtime 맵에 단일 runtime을 주입합니다.
     *
     * <p>실제 리로드 경로를 거치려면 유효한 JAR 생성이 필요하므로,
     * 본 테스트에서는 reflection으로 최소 runtime만 주입해
     * "삭제/롤백 정책" 자체를 검증합니다.</p>
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
            throw new IllegalStateException("테스트 runtime 주입에 실패했습니다.", ex);
        }
    }

    /**
     * 테스트용 최소 액션 레지스트리를 생성합니다.
     */
    private static BusinessWorkflowActionRegistry createTestActionRegistry() {
        final SocketActionExecutor executor = new SocketActionExecutor() {
            /**
             * execute 기능을 수행합니다.
             *
             * @param context 입력 값
             */

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
     * manager 내부 private record(PluginRuntime) 타입을 조회합니다.
     */
    private static Class<?> findPluginRuntimeClass() {
        for (Class<?> nestedClass : BusinessWorkflowPluginRuntimeManager.class.getDeclaredClasses()) {
            if ("PluginRuntime".equals(nestedClass.getSimpleName())) {
                return nestedClass;
            }
        }
        throw new IllegalStateException("PluginRuntime nested class를 찾지 못했습니다.");
    }

    /**
     * 테스트용 eqp 레코드를 생성합니다.
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
     * 테스트용 invalid plugin jar 레코드를 생성합니다.
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
     * {@link TcEqpStore} 테스트 더블입니다.
     */
    private static final class FakeEqpStore implements TcEqpStore {
        private final List<TcEqp> eqps;
        private int findAllCallCount = 0;

        /**
         * FakeEqpStore 생성자를 초기화합니다.
         *
         * @param eqps 입력 값
         */

        private FakeEqpStore(final List<TcEqp> eqps) {
            this.eqps = new ArrayList<>(eqps);
        }

        /**
         * upsert 기능을 수행합니다.
         *
         * @param command 입력 값
         * @return 처리 결과
         */

        @Override
        public TcEqp upsert(final UpsertTcEqp command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findByEqpId 기능을 수행합니다.
         *
         * @param eqpId 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcEqp> findByEqpId(final String eqpId) {
            return eqps.stream()
                    .filter(eqp -> eqpId != null && eqpId.equals(eqp.eqpId()))
                    .findFirst();
        }

        /**
         * findAll 기능을 수행합니다.
         *
         * @param page 입력 값
         * @return 처리 결과
         */

        @Override
        public List<TcEqp> findAll(final PageRequest page) {
            findAllCallCount++;
            final int from = Math.min(page.offset(), eqps.size());
            final int to = Math.min(from + page.limit(), eqps.size());
            return List.copyOf(eqps.subList(from, to));
        }

        /**
         * deleteByEqpId 기능을 수행합니다.
         *
         * @param eqpId 입력 값
         */

        @Override
        public void deleteByEqpId(final String eqpId) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /**
     * {@link TcJarBusinessStore} 테스트 더블입니다.
     */
    private static final class FakeJarBusinessStore implements TcJarBusinessStore {
        private final Map<Long, TcJarBusiness> jarByEqpKey;

        /**
         * FakeJarBusinessStore 생성자를 초기화합니다.
         *
         * @param jarByEqpKey 입력 값
         */

        private FakeJarBusinessStore(final Map<Long, TcJarBusiness> jarByEqpKey) {
            this.jarByEqpKey = new LinkedHashMap<>(jarByEqpKey);
        }

        /**
         * upsert 기능을 수행합니다.
         *
         * @param command 입력 값
         * @return 처리 결과
         */

        @Override
        public TcJarBusiness upsert(final UpsertTcJarBusiness command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findByEqpKey 기능을 수행합니다.
         *
         * @param eqpKey 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcJarBusiness> findByEqpKey(final long eqpKey) {
            return Optional.ofNullable(jarByEqpKey.get(eqpKey));
        }

        /**
         * deleteByEqpKey 기능을 수행합니다.
         *
         * @param eqpKey 입력 값
         */

        @Override
        public void deleteByEqpKey(final long eqpKey) {
            throw new UnsupportedOperationException("not used");
        }
    }
}


