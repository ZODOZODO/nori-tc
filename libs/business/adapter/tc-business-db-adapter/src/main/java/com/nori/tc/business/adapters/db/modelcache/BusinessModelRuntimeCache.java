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
 * Business model runtime 메모리 캐시입니다.
 *
 * <p>핵심 규칙:
 * - DB 스냅샷은 로컬 객체로 완성한 뒤 원자적으로 swap합니다.
 * - 부분 실패 시 기존 스냅샷을 유지합니다.</p>
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
     * 필요한 DB store/assembler를 주입받습니다.
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
     * 기동 시 초기 스냅샷 로딩을 수행합니다.
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
     * 전체 eqp/model 스냅샷을 다시 로딩합니다.
     */
    public void reloadAll() {
        final Map<String, Long> eqpModelBindings = loadEqpModelBindings();
        final Set<Long> modelKeys = new LinkedHashSet<>(eqpModelBindings.values());

        final Map<Long, TcModelRuntime> modelRuntimes = new LinkedHashMap<>();
        for (Long modelKey : modelKeys) {
            if (modelKey == null || modelKey <= 0L) {
                log.warn("Invalid modelKey in binding map. modelKey={}", modelKey);
                continue;
            }
            modelRuntimes.put(modelKey, runtimeAssembler.assemble(modelKey));
        }

        final BusinessModelRuntimeSnapshot nextSnapshot =
                BusinessModelRuntimeSnapshot.of(eqpModelBindings, modelRuntimes);
        snapshotRef.set(nextSnapshot);

        log.info("Business model runtime cache reloaded. eqpBindings={}, modelRuntimes={}",
                nextSnapshot.bindingCount(),
                nextSnapshot.runtimeCount());
    }

    /**
     * 특정 modelKey runtime만 재조립 후 스냅샷을 교체합니다.
     *
     * @param modelKey model key
     */
    public void reloadModelRuntime(final long modelKey) {
        if (modelKey <= 0L) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }

        final TcModelRuntime runtime = runtimeAssembler.assemble(modelKey);
        while (true) {
            final BusinessModelRuntimeSnapshot current = snapshotRef.get();
            final Map<Long, TcModelRuntime> nextRuntimes = new LinkedHashMap<>(current.modelRuntimes());
            nextRuntimes.put(modelKey, runtime);

            final BusinessModelRuntimeSnapshot next = BusinessModelRuntimeSnapshot.of(
                    current.eqpModelBindings(),
                    nextRuntimes
            );
            if (snapshotRef.compareAndSet(current, next)) {
                log.info("Model runtime reloaded. modelKey={}, runtimeCount={}", modelKey, next.runtimeCount());
                return;
            }
        }
    }

    /**
     * eqpId -> modelKey 바인딩을 원자적으로 갱신합니다.
     *
     * @param eqpId 장비 ID
     * @param modelKey model key
     */
    public void updateEqpBinding(final String eqpId, final long modelKey) {
        final String normalizedEqpId = normalizeEqpId(eqpId);
        if (normalizedEqpId == null) {
            throw new IllegalArgumentException("eqpId is required");
        }
        if (modelKey <= 0L) {
            throw new IllegalArgumentException("modelKey must be > 0");
        }

        final BusinessModelRuntimeSnapshot current = snapshotRef.get();
        final TcModelRuntime runtime = current.findRuntimeByModelKey(modelKey)
                .orElseGet(() -> runtimeAssembler.assemble(modelKey));

        while (true) {
            final BusinessModelRuntimeSnapshot base = snapshotRef.get();
            final Map<String, Long> nextBindings = new LinkedHashMap<>(base.eqpModelBindings());
            nextBindings.put(normalizedEqpId, modelKey);

            final Map<Long, TcModelRuntime> nextRuntimes = new LinkedHashMap<>(base.modelRuntimes());
            nextRuntimes.put(modelKey, runtime);

            final BusinessModelRuntimeSnapshot next = BusinessModelRuntimeSnapshot.of(nextBindings, nextRuntimes);
            if (snapshotRef.compareAndSet(base, next)) {
                log.info("Eqp binding updated. eqpId={}, modelKey={}, bindingCount={}, runtimeCount={}",
                        normalizedEqpId,
                        modelKey,
                        next.bindingCount(),
                        next.runtimeCount());
                return;
            }
        }
    }

    /**
     * currentSnapshot 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    @Override
    public BusinessModelRuntimeSnapshot currentSnapshot() {
        return snapshotRef.get();
    }

    /**
     * loadEqpModelBindings 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    private Map<String, Long> loadEqpModelBindings() {
        final List<TcEqp> allEqps = loadAllEqps();
        final Map<String, Long> bindings = new LinkedHashMap<>();
        for (TcEqp eqp : allEqps) {
            if (eqp == null) {
                continue;
            }
            final String eqpId = normalizeEqpId(eqp.eqpId());
            if (eqpId == null) {
                log.warn("Skipping eqp binding because eqpId is blank. eqpKey={}", eqp.eqpKey());
                continue;
            }

            /*
             * 부팅 시점 DB 스냅샷 적재에서도 설비 단위 로그를 분리하기 위해
             * eqpId MDC 스코프를 명시적으로 열고 바인딩을 기록합니다.
             */
            try (BusinessLogContext ignored = BusinessLogContext.withEqpId(eqpId)) {
                bindings.put(eqpId, eqp.modelKey());
                if (log.isDebugEnabled()) {
                    log.debug("Boot binding loaded. eqpId={}, modelKey={}", eqpId, eqp.modelKey());
                }
            }
        }
        return bindings;
    }

    /**
     * eqpId -> modelKey 바인딩을 제거합니다.
     *
     * <p>핵심 정책:</p>
     * <p>1) 바인딩 제거는 항상 원자적(CAS) 스냅샷 교체로 수행합니다.</p>
     * <p>2) 제거한 modelKey를 참조하는 eqp가 0개가 되면 model runtime 캐시도 같이 제거합니다.</p>
     *
     * @param eqpId 제거할 설비 ID
     * @return 제거된 modelKey(optional)
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
                final Long removedModelKey = base.eqpModelBindings().get(normalizedEqpId);
                if (removedModelKey == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Eqp binding remove skipped because binding is absent. eqpId={}", normalizedEqpId);
                    }
                    return Optional.empty();
                }

                final Map<String, Long> nextBindings = new LinkedHashMap<>(base.eqpModelBindings());
                nextBindings.remove(normalizedEqpId);

                final Map<Long, TcModelRuntime> nextRuntimes = new LinkedHashMap<>(base.modelRuntimes());
                final boolean modelRuntimeStillReferenced = nextBindings.containsValue(removedModelKey);
                final boolean modelRuntimeRemoved = !modelRuntimeStillReferenced && nextRuntimes.remove(removedModelKey) != null;

                final BusinessModelRuntimeSnapshot next = BusinessModelRuntimeSnapshot.of(nextBindings, nextRuntimes);
                if (snapshotRef.compareAndSet(base, next)) {
                    log.info(
                            "Eqp binding removed. eqpId={}, removedModelKey={}, modelRuntimeRemoved={}, bindingCount={}, runtimeCount={}",
                            normalizedEqpId,
                            removedModelKey,
                            modelRuntimeRemoved,
                            next.bindingCount(),
                            next.runtimeCount()
                    );
                    if (log.isDebugEnabled() && !modelRuntimeRemoved) {
                        log.debug(
                                "Model runtime preserved because other eqp still reference the modelKey. eqpId={}, modelKey={}",
                                normalizedEqpId,
                                removedModelKey
                        );
                    }
                    return Optional.of(removedModelKey);
                }
            }
        }
    }

    /**
     * loadAllEqps 기능을 수행합니다.
     *
     * @return 처리 결과
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
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
