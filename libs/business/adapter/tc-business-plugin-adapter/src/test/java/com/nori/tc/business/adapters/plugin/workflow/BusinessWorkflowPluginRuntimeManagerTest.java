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
 * {@link BusinessWorkflowPluginRuntimeManager}의 Step14(preload 정책) 동작을 검증하는 단위 테스트입니다.
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
     * 테스트용 plugin runtime 프로퍼티를 생성하고 즉시 유효성 검증까지 수행합니다.
     *
     * <p>각 테스트가 설정 바인딩을 거치지 않고 직접 객체를 생성하므로, 운영 환경에서 필수인 값도
     * 테스트 헬퍼에서 명시적으로 채워야 합니다.</p>
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
        /*
         * 테스트는 `BusinessWorkflowPluginRuntimeProperties#validate()`를 직접 호출하므로,
         * 운영 코드에서 추가된 필수 설정값(`maxJarBytes`)도 함께 채워야 합니다.
         * 본 테스트에서는 JAR 크기 제한 동작 자체를 검증하지 않으므로, 충분히 큰 고정값을 사용해
         * 개별 테스트 의도(preload/reload 동작)와 무관한 검증 실패를 제거합니다.
         */
        properties.setMaxJarBytes(10L * 1024L * 1024L);
        properties.validate();
        return properties;
    }

    /**
     * 테스트를 위해 manager 내부 런타임 맵에 최소 구성 runtime을 직접 주입합니다.
     *
     * <p>실제 리로드 경로를 타면 유효한 JAR 생성/로딩까지 필요하므로 준비 비용이 커집니다.
     * 이 테스트는 제거/롤백 정책 검증이 목적이므로 reflection으로 내부 `PluginRuntime`만 만들어
     * 주입하여 상태 전이만 검증합니다.</p>
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
     *
     * <p>리플렉션으로 생성하는 `PluginRuntime`에 레지스트리가 필요하므로, 실제 비즈니스 로직 대신
     * no-op 액션 하나만 등록한 경량 레지스트리를 사용합니다.</p>
     */
    private static BusinessWorkflowActionRegistry createTestActionRegistry() {
        final AbstractSocketActionExecutor executor = new AbstractSocketActionExecutor() {
            /**
             * 테스트용 no-op 액션 메서드입니다.
             *
             * @param context 액션 실행 컨텍스트
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
     * manager 내부 private record(`PluginRuntime`) 타입을 조회합니다.
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
    private static TcEqp createEqp(final long eqpKey, final String eqpId, final long modelVersionKey) {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcEqp(
                eqpKey,
                eqpId,
                ProtocolType.SOCKET,
                "ACTIVE",
                false,
                0,
                "127.0.0.1",
                5000,
                modelVersionKey,
                null,
                true,
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }

    /**
     * 테스트용 invalid plugin jar 레코드를 생성합니다.
     *
     * <p>JAR 파싱 실패 경로를 재현하기 위해 의도적으로 JAR 형식이 아닌 바이트를 저장합니다.</p>
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
     *
     * <p>preload/reload 테스트에 필요한 최소 조회 기능만 구현하고, 범위 밖 메서드는 예외를 던집니다.</p>
     */
    private static final class FakeEqpStore implements TcEqpStore {
        private final List<TcEqp> eqps;
        private int findAllCallCount = 0;

        /**
         * 테스트에서 사용할 설비 목록으로 스토어를 초기화합니다.
         *
         * @param eqps 조회 대상으로 사용할 설비 목록
         */

        private FakeEqpStore(final List<TcEqp> eqps) {
            this.eqps = new ArrayList<>(eqps);
        }

        /**
         * 테스트 범위 밖 메서드입니다.
         *
         * @param command 업서트 명령
         * @return 반환되지 않음
         */

        @Override
        public TcEqp upsert(final UpsertTcEqp command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * 설비 ID로 테스트용 설비 레코드를 조회합니다.
         *
         * @param eqpId 조회할 설비 ID
         * @return 일치하는 설비가 있으면 반환, 없으면 빈 값
         */

        @Override
        public Optional<TcEqp> findByEqpId(final String eqpId) {
            return eqps.stream()
                    .filter(eqp -> eqpId != null && eqpId.equals(eqp.eqpId()))
                    .findFirst();
        }

        /**
         * 페이지 조건으로 설비 목록을 조회합니다.
         *
         * <p>preload 테스트에서 DB 조회 호출 여부를 검증하기 위해 호출 횟수를 함께 증가시킵니다.</p>
         *
         * @param page 페이지 요청 정보
         * @return 요청 범위에 해당하는 설비 목록
         */

        @Override
        public List<TcEqp> findAll(final PageRequest page) {
            findAllCallCount++;
            final int from = Math.min(page.offset(), eqps.size());
            final int to = Math.min(from + page.limit(), eqps.size());
            return List.copyOf(eqps.subList(from, to));
        }

        /**
         * route_partition + enabled 조건으로 설비 목록을 조회합니다.
         *
         * <p>본 테스트는 preload/reload 정책 검증이 목적이므로,
         * 실제 DB 구현처럼 복잡한 정렬/최적화 없이 단순 필터 + 페이지 계산만 수행합니다.</p>
         * <p>U3 계약 확장 후 테스트 더블이 인터페이스를 계속 만족하도록 추가한 구현입니다.</p>
         *
         * @param routePartitions 조회 대상 route_partition 목록
         * @param enabled enabled 필터 값
         * @param page 페이지 요청 정보
         * @return 필터링 및 페이징이 적용된 설비 목록
         */
        @Override
        public List<TcEqp> findAllByRoutePartitionsAndEnabled(
                final List<Integer> routePartitions,
                final boolean enabled,
                final PageRequest page
        ) {
            findAllCallCount++;
            if (routePartitions == null || routePartitions.isEmpty()) {
                return List.of();
            }

            final List<TcEqp> filtered = eqps.stream()
                    .filter(eqp -> eqp.enabled() == enabled)
                    .filter(eqp -> eqp.routePartition() != null && routePartitions.contains(eqp.routePartition()))
                    .toList();

            final int from = Math.min(page.offset(), filtered.size());
            final int to = Math.min(from + page.limit(), filtered.size());
            return List.copyOf(filtered.subList(from, to));
        }

        /**
         * 테스트 범위 밖 메서드입니다.
         *
         * @param eqpId 삭제 대상 설비 ID
         */

        @Override
        public void deleteByEqpId(final String eqpId) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /**
     * {@link TcJarBusinessStore} 테스트 더블입니다.
     *
     * <p>설비 키 기준 JAR 조회만 구현하여 리로드 시나리오 검증에 사용합니다.</p>
     */
    private static final class FakeJarBusinessStore implements TcJarBusinessStore {
        private final Map<Long, TcJarBusiness> jarByEqpKey;

        /**
         * 설비 키별 JAR 매핑으로 스토어를 초기화합니다.
         *
         * @param jarByEqpKey 설비 키 -> JAR 레코드 매핑
         */

        private FakeJarBusinessStore(final Map<Long, TcJarBusiness> jarByEqpKey) {
            this.jarByEqpKey = new LinkedHashMap<>(jarByEqpKey);
        }

        /**
         * 테스트 범위 밖 메서드입니다.
         *
         * @param command 업서트 명령
         * @return 반환되지 않음
         */

        @Override
        public TcJarBusiness upsert(final UpsertTcJarBusiness command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * 설비 키로 JAR 레코드를 조회합니다.
         *
         * @param eqpKey 조회할 설비 키
         * @return 매핑된 JAR 레코드가 있으면 반환, 없으면 빈 값
         */

        @Override
        public Optional<TcJarBusiness> findByEqpKey(final long eqpKey) {
            return Optional.ofNullable(jarByEqpKey.get(eqpKey));
        }

        /**
         * 테스트 범위 밖 메서드입니다.
         *
         * @param eqpKey 삭제 대상 설비 키
         */

        @Override
        public void deleteByEqpKey(final long eqpKey) {
            throw new UnsupportedOperationException("not used");
        }
    }
}


