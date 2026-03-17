package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.business.core.logging.BusinessLogContext;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeMutationPort;
import com.nori.tc.business.domain.modelcache.BusinessModelRuntimeSnapshot;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.domain.eqp.TcEqp;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Business 모델 런타임 캐시입니다.
 *
 * <p>
 * 캐시 갱신은 항상 새 스냅샷을 만든 뒤 CAS로 교체하여,
 * 읽기 경로가 부분 상태를 보지 않도록 보장합니다.
 * </p>
 */
@Component
public class BusinessModelRuntimeCache implements BusinessModelRuntimeMutationPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessModelRuntimeCache.class);

    private final TcEqpStore eqpStore;
    private final BusinessModelRuntimeAssembler runtimeAssembler;
    private final BusinessModelCacheProperties cacheProperties;
    private final AtomicReference<BusinessModelRuntimeSnapshot> snapshotRef =
            new AtomicReference<>(BusinessModelRuntimeSnapshot.empty());

    /**
     * 캐시 구성 요소를 주입받습니다.
     */
    public BusinessModelRuntimeCache(
            final TcEqpStore eqpStore,
            final BusinessModelRuntimeAssembler runtimeAssembler,
            final BusinessModelCacheProperties cacheProperties
    ) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        this.runtimeAssembler = Objects.requireNonNull(runtimeAssembler, "runtimeAssembler is null");
        this.cacheProperties = Objects.requireNonNull(cacheProperties, "cacheProperties is null");
    }

    /**
     * 애플리케이션 기동 시 초기 캐시 로드를 수행합니다.
     */
    @PostConstruct
    public void initialize() {
        if (!cacheProperties.isLoadOnStartup()) {
            log.info("Business model runtime cache bootstrap skipped. loadOnStartup=false");
            return;
        }

        try {
            reloadAll();
        } catch (RuntimeException ex) {
            log.error("Business model runtime cache bootstrap failed.", ex);
            if (cacheProperties.isFailFastOnStartup()) {
                throw ex;
            }
        }
    }

    /**
     * 전체 eqp/model 런타임 스냅샷을 다시 적재합니다.
     */
    public void reloadAll() {
        final List<TcEqp> allEqps = loadAllEqps();
        final Map<String, Long> eqpModelBindings = buildEqpModelBindings(allEqps);
        final Map<String, Long> eqpKeyBindings = buildEqpKeyBindings(allEqps);
        final Set<Long> modelVersionKeys = new LinkedHashSet<>(eqpModelBindings.values());

        final Map<Long, TcModelRuntime> modelRuntimes = new LinkedHashMap<>();
        for (Long modelVersionKey : modelVersionKeys) {
            if (modelVersionKey == null || modelVersionKey <= 0L) {
                log.warn("Invalid modelVersionKey in binding map. modelVersionKey={}", modelVersionKey);
                continue;
            }
            try {
                modelRuntimes.put(modelVersionKey, runtimeAssembler.assemble(modelVersionKey));
            } catch (RuntimeException ex) {
                log.error("Model runtime assemble failed during reloadAll. modelVersionKey={}", modelVersionKey, ex);
                throw ex;
            }
        }

        final BusinessModelRuntimeSnapshot nextSnapshot =
                BusinessModelRuntimeSnapshot.of(eqpModelBindings, modelRuntimes, eqpKeyBindings);
        snapshotRef.set(nextSnapshot);

        log.info("Business model runtime cache reloaded. eqpBindings={}, modelRuntimes={}",
                nextSnapshot.bindingCount(),
                nextSnapshot.runtimeCount());
    }

    /**
     * 특정 modelVersionKey 런타임만 갱신합니다.
     */
    public void reloadModelRuntime(final long modelVersionKey) {
        if (modelVersionKey <= 0L) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }

        final TcModelRuntime runtime;
        try {
            runtime = runtimeAssembler.assemble(modelVersionKey);
        } catch (RuntimeException ex) {
            log.error("Model runtime assemble failed during reloadModelRuntime. modelVersionKey={}", modelVersionKey, ex);
            throw ex;
        }

        while (true) {
            final BusinessModelRuntimeSnapshot current = snapshotRef.get();
            final Map<Long, TcModelRuntime> nextRuntimes = new LinkedHashMap<>(current.modelRuntimes());
            nextRuntimes.put(modelVersionKey, runtime);

            final BusinessModelRuntimeSnapshot next = BusinessModelRuntimeSnapshot.of(
                    current.eqpModelBindings(),
                    nextRuntimes,
                    current.eqpKeyBindings()
            );
            if (snapshotRef.compareAndSet(current, next)) {
                log.info("Model runtime reloaded. modelVersionKey={}, runtimeCount={}", modelVersionKey, next.runtimeCount());
                return;
            }
        }
    }

    /**
     * eqpId -> modelVersionKey 바인딩을 갱신합니다.
     *
     * <p>eqpKey 바인딩도 함께 갱신하기 위해 DB에서 TcEqp를 조회합니다.</p>
     */
    public void updateEqpBinding(final String eqpId, final long modelVersionKey) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        if (normalizedEqpId == null) {
            throw new IllegalArgumentException("eqpId is required");
        }
        if (modelVersionKey <= 0L) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }

        final BusinessModelRuntimeSnapshot current = snapshotRef.get();
        final TcModelRuntime runtime = current.findRuntimeByModelVersionKey(modelVersionKey)
                .orElseGet(() -> runtimeAssembler.assemble(modelVersionKey));

        // eqpKey 조회: eqpId → DB에서 TcEqp 로드
        final Long eqpKey = eqpStore.findByEqpId(normalizedEqpId)
                .map(TcEqp::eqpKey)
                .orElse(null);

        while (true) {
            final BusinessModelRuntimeSnapshot base = snapshotRef.get();
            final Map<String, Long> nextBindings = new LinkedHashMap<>(base.eqpModelBindings());
            nextBindings.put(normalizedEqpId, modelVersionKey);

            final Map<Long, TcModelRuntime> nextRuntimes = new LinkedHashMap<>(base.modelRuntimes());
            nextRuntimes.put(modelVersionKey, runtime);

            // eqpKey가 유효한 경우에만 eqpKey 바인딩 갱신
            final Map<String, Long> nextEqpKeyBindings = new LinkedHashMap<>(base.eqpKeyBindings());
            if (eqpKey != null && eqpKey > 0L) {
                nextEqpKeyBindings.put(normalizedEqpId, eqpKey);
            }

            final BusinessModelRuntimeSnapshot next =
                    BusinessModelRuntimeSnapshot.of(nextBindings, nextRuntimes, nextEqpKeyBindings);
            if (snapshotRef.compareAndSet(base, next)) {
                log.info("Eqp binding updated. eqpId={}, modelVersionKey={}, eqpKey={}, bindingCount={}, runtimeCount={}",
                        normalizedEqpId,
                        modelVersionKey,
                        eqpKey,
                        next.bindingCount(),
                        next.runtimeCount());
                return;
            }
        }
    }

    /**
     * 현재 스냅샷을 반환합니다.
     */
    @Override
    public BusinessModelRuntimeSnapshot currentSnapshot() {
        return snapshotRef.get();
    }

    /**
     * TcEqp 목록에서 eqpId → modelVersionKey 매핑을 구성합니다.
     */
    private Map<String, Long> buildEqpModelBindings(final List<TcEqp> allEqps) {
        final Map<String, Long> bindings = new LinkedHashMap<>();
        for (TcEqp eqp : allEqps) {
            if (eqp == null) {
                continue;
            }
            final String eqpId = normalizeEqpId(eqp.eqpId());
            if (eqpId == null) {
                log.warn("Skipping eqp model binding because eqpId is blank. eqpKey={}", eqp.eqpKey());
                continue;
            }

            // 장비 단위 MDC를 분리해서 부트 적재 로그 상관분석을 쉽게 합니다.
            try (BusinessLogContext ignored = BusinessLogContext.withEqpId(eqpId)) {
                bindings.put(eqpId, eqp.modelVersionKey());
                log.debug("Boot model binding loaded. eqpId={}, modelVersionKey={}", eqpId, eqp.modelVersionKey());
            }
        }
        return bindings;
    }

    /**
     * TcEqp 목록에서 eqpId → eqpKey(DB PK) 매핑을 구성합니다.
     */
    private Map<String, Long> buildEqpKeyBindings(final List<TcEqp> allEqps) {
        final Map<String, Long> bindings = new LinkedHashMap<>();
        for (TcEqp eqp : allEqps) {
            if (eqp == null) {
                continue;
            }
            final String eqpId = normalizeEqpId(eqp.eqpId());
            if (eqpId == null) {
                log.warn("Skipping eqp key binding because eqpId is blank. eqpKey={}", eqp.eqpKey());
                continue;
            }
            if (eqp.eqpKey() <= 0L) {
                log.warn("Skipping eqp key binding because eqpKey is invalid. eqpId={}, eqpKey={}", eqpId, eqp.eqpKey());
                continue;
            }
            bindings.put(eqpId, eqp.eqpKey());
        }
        return bindings;
    }

    /**
     * eqpId -> modelVersionKey 바인딩을 제거합니다.
     *
     * <p>
     * 제거된 modelVersionKey를 참조하는 장비가 더 없으면 해당 runtime도 같이 제거합니다.
     * </p>
     */
    @Override
    public Optional<Long> removeEqpBinding(final String eqpId) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        if (normalizedEqpId == null) {
            throw new IllegalArgumentException("eqpId is required");
        }

        try (BusinessLogContext ignored = BusinessLogContext.withEqpId(normalizedEqpId)) {
            while (true) {
                final BusinessModelRuntimeSnapshot base = snapshotRef.get();
                final Long removedModelVersionKey = base.eqpModelBindings().get(normalizedEqpId);
                if (removedModelVersionKey == null) {
                    log.debug("Eqp binding remove skipped because binding is absent. eqpId={}", normalizedEqpId);
                    return Optional.empty();
                }

                final Map<String, Long> nextBindings = new LinkedHashMap<>(base.eqpModelBindings());
                nextBindings.remove(normalizedEqpId);

                final Map<Long, TcModelRuntime> nextRuntimes = new LinkedHashMap<>(base.modelRuntimes());
                final boolean modelRuntimeStillReferenced = nextBindings.containsValue(removedModelVersionKey);
                final boolean modelRuntimeRemoved = !modelRuntimeStillReferenced
                        && nextRuntimes.remove(removedModelVersionKey) != null;

                // eqpKey 바인딩도 함께 제거
                final Map<String, Long> nextEqpKeyBindings = new LinkedHashMap<>(base.eqpKeyBindings());
                nextEqpKeyBindings.remove(normalizedEqpId);

                final BusinessModelRuntimeSnapshot next =
                        BusinessModelRuntimeSnapshot.of(nextBindings, nextRuntimes, nextEqpKeyBindings);
                if (snapshotRef.compareAndSet(base, next)) {
                    log.info(
                            "Eqp binding removed. eqpId={}, removedModelVersionKey={}, modelRuntimeRemoved={}, bindingCount={}, runtimeCount={}",
                            normalizedEqpId,
                            removedModelVersionKey,
                            modelRuntimeRemoved,
                            next.bindingCount(),
                            next.runtimeCount()
                    );
                    if (!modelRuntimeRemoved && log.isDebugEnabled()) {
                        log.debug(
                                "Model runtime preserved because another eqp still references the modelVersionKey. eqpId={}, modelVersionKey={}",
                                normalizedEqpId,
                                removedModelVersionKey
                        );
                    }
                    return Optional.of(removedModelVersionKey);
                }
            }
        }
    }

    /**
     * eqp 전체 목록을 페이지 단위로 조회합니다.
     */
    private List<TcEqp> loadAllEqps() {
        final List<TcEqp> results = new ArrayList<>();
        final int pageSize = cacheProperties.getPageSize();
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

    /**
     * eqpId를 trim하고 빈 문자열이면 null을 반환합니다.
     */
    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null) {
            return null;
        }
        final String normalized = eqpId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
