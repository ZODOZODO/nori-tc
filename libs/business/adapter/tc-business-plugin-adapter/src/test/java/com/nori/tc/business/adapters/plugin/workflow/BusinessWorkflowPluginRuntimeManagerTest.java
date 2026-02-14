package com.nori.tc.business.adapters.plugin.workflow;

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

        private FakeEqpStore(final List<TcEqp> eqps) {
            this.eqps = new ArrayList<>(eqps);
        }

        @Override
        public TcEqp upsert(final UpsertTcEqp command) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<TcEqp> findByEqpId(final String eqpId) {
            return eqps.stream()
                    .filter(eqp -> eqpId != null && eqpId.equals(eqp.eqpId()))
                    .findFirst();
        }

        @Override
        public List<TcEqp> findAll(final PageRequest page) {
            findAllCallCount++;
            final int from = Math.min(page.offset(), eqps.size());
            final int to = Math.min(from + page.limit(), eqps.size());
            return List.copyOf(eqps.subList(from, to));
        }

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

        private FakeJarBusinessStore(final Map<Long, TcJarBusiness> jarByEqpKey) {
            this.jarByEqpKey = new LinkedHashMap<>(jarByEqpKey);
        }

        @Override
        public TcJarBusiness upsert(final UpsertTcJarBusiness command) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<TcJarBusiness> findByEqpKey(final long eqpKey) {
            return Optional.ofNullable(jarByEqpKey.get(eqpKey));
        }

        @Override
        public void deleteByEqpKey(final long eqpKey) {
            throw new UnsupportedOperationException("not used");
        }
    }
}


