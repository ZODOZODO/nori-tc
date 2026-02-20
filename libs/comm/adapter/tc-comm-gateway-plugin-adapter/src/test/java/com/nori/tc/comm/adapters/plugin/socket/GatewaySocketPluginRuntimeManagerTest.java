package com.nori.tc.comm.adapters.plugin.socket;

import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.eqp.upsert.UpsertTcEqp;
import com.nori.tc.db.core.jar.store.TcJarGatewayStore;
import com.nori.tc.db.core.jar.upsert.UpsertTcJarGateway;
import com.nori.tc.db.domain.common.model.ProtocolType;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.jar.TcJarGateway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link GatewaySocketPluginRuntimeManager} preload 정책 단위 테스트입니다.
 *
 * <p>검증 포인트:</p>
 * <p>1) loadOnStartup=false 일 때 preload 미실행</p>
 * <p>2) failFastOnStartup=false 일 때 preload 실패를 허용하고 기동 지속</p>
 * <p>3) failFastOnStartup=true 일 때 preload 실패 시 예외 전파</p>
 */
class GatewaySocketPluginRuntimeManagerTest {

    /**
     * 테스트에서 사용하는 플러그인 JAR 최대 허용 크기(byte)입니다.
     *
     * <p>운영 기본값과 동일한 수준으로 맞춰 속성 검증 로직과 테스트 fixture가
     * 항상 같은 전제를 갖도록 유지합니다.</p>
     */
    private static final long TEST_MAX_JAR_BYTES = 10L * 1024L * 1024L;

    /**
     * loadOnStartup=false 인 경우 preload 를 시도하지 않아야 합니다.
     */
    @Test
    void shouldSkipPreloadWhenLoadOnStartupIsDisabled() {
        final FakeEqpStore eqpStore = new FakeEqpStore(List.of(
                createSocketEqp(1L, "EQP-01", 101L)
        ));
        final FakeJarGatewayStore jarStore = new FakeJarGatewayStore(Map.of(
                1L, createInvalidJar(1L)
        ));

        final GatewaySocketPluginRuntimeProperties properties = createProperties(false, true, 100);
        final GatewaySocketPluginRuntimeManager manager =
                new GatewaySocketPluginRuntimeManager(eqpStore, jarStore, properties);

        manager.initialize();

        Assertions.assertEquals(0, eqpStore.findAllCallCount, "loadOnStartup=false 인 경우 DB preload 조회가 수행되면 안 됩니다.");
        Assertions.assertTrue(manager.findByEqpId("EQP-01").isEmpty(), "플러그인 런타임이 없어야 합니다.");
    }

    /**
     * failFastOnStartup=false 인 경우 preload 실패를 로깅 후 무시하고 기동을 계속해야 합니다.
     */
    @Test
    void shouldContinueWhenPreloadFailsAndFailFastIsDisabled() {
        final FakeEqpStore eqpStore = new FakeEqpStore(List.of(
                createSocketEqp(1L, "EQP-01", 101L)
        ));
        final FakeJarGatewayStore jarStore = new FakeJarGatewayStore(Map.of(
                1L, createInvalidJar(1L)
        ));

        final GatewaySocketPluginRuntimeProperties properties = createProperties(true, false, 100);
        final GatewaySocketPluginRuntimeManager manager =
                new GatewaySocketPluginRuntimeManager(eqpStore, jarStore, properties);

        manager.initialize();

        Assertions.assertTrue(manager.findByEqpId("EQP-01").isEmpty(), "preload 실패 시 런타임은 비어 있어야 합니다.");
    }

    /**
     * failFastOnStartup=true 인 경우 preload 실패를 예외로 전파해야 합니다.
     */
    @Test
    void shouldThrowWhenPreloadFailsAndFailFastIsEnabled() {
        final FakeEqpStore eqpStore = new FakeEqpStore(List.of(
                createSocketEqp(1L, "EQP-01", 101L)
        ));
        final FakeJarGatewayStore jarStore = new FakeJarGatewayStore(Map.of(
                1L, createInvalidJar(1L)
        ));

        final GatewaySocketPluginRuntimeProperties properties = createProperties(true, true, 100);
        final GatewaySocketPluginRuntimeManager manager =
                new GatewaySocketPluginRuntimeManager(eqpStore, jarStore, properties);

        Assertions.assertThrows(IllegalStateException.class, manager::initialize);
    }

    /**
     * 테스트용 플러그인 런타임 프로퍼티를 생성합니다.
     */
    private static GatewaySocketPluginRuntimeProperties createProperties(
            final boolean loadOnStartup,
            final boolean failFastOnStartup,
            final int pageSize
    ) {
        final GatewaySocketPluginRuntimeProperties properties = new GatewaySocketPluginRuntimeProperties();
        properties.setLoadOnStartup(loadOnStartup);
        properties.setFailFastOnStartup(failFastOnStartup);
        properties.setPageSize(pageSize);
        properties.setMaxJarBytes(TEST_MAX_JAR_BYTES);
        properties.validate();
        return properties;
    }

    /**
     * 테스트용 SOCKET 설비 레코드를 생성합니다.
     */
    private static TcEqp createSocketEqp(final long eqpKey, final String eqpId, final long modelKey) {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcEqp(
                eqpKey,
                eqpId,
                ProtocolType.SOCKET,
                "127.0.0.1",
                5001,
                modelKey,
                true,
                now,
                now,
                "SYSTEM",
                "SYSTEM"
        );
    }

    /**
     * 테스트용 invalid gateway plugin jar 레코드를 생성합니다.
     */
    private static TcJarGateway createInvalidJar(final long eqpKey) {
        final OffsetDateTime now = OffsetDateTime.now();
        return new TcJarGateway(
                eqpKey,
                "invalid-gateway-plugin.jar",
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
     * {@link TcJarGatewayStore} 테스트 더블입니다.
     */
    private static final class FakeJarGatewayStore implements TcJarGatewayStore {
        private final Map<Long, TcJarGateway> jarByEqpKey;

        /**
         * FakeJarGatewayStore 생성자를 초기화합니다.
         *
         * @param jarByEqpKey 입력 값
         */

        private FakeJarGatewayStore(final Map<Long, TcJarGateway> jarByEqpKey) {
            this.jarByEqpKey = new LinkedHashMap<>(jarByEqpKey);
        }

        /**
         * upsert 기능을 수행합니다.
         *
         * @param command 입력 값
         * @return 처리 결과
         */

        @Override
        public TcJarGateway upsert(final UpsertTcJarGateway command) {
            throw new UnsupportedOperationException("not used");
        }

        /**
         * findByEqpKey 기능을 수행합니다.
         *
         * @param eqpKey 입력 값
         * @return 처리 결과
         */

        @Override
        public Optional<TcJarGateway> findByEqpKey(final long eqpKey) {
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
