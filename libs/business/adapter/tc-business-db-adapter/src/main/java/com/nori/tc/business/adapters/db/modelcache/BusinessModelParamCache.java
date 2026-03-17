package com.nori.tc.business.adapters.db.modelcache;

import com.nori.tc.business.core.modelcache.BusinessModelParamMutationPort;
import com.nori.tc.business.core.modelcache.BusinessModelParamProvider;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeProvider;
import com.nori.tc.business.domain.modelcache.BusinessModelParamSnapshot;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.eqp.store.TcEqpParamStore;
import com.nori.tc.db.core.eqp.store.TcEqpStore;
import com.nori.tc.db.core.model.store.TcModelParamStore;
import com.nori.tc.db.domain.eqp.TcEqp;
import com.nori.tc.db.domain.eqp.TcEqpParam;
import com.nori.tc.db.domain.model.TcModelParam;
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
 * Business model/eqp 파라미터 in-memory 캐시입니다.
 *
 * <p>
 * DB에서 매번 조회하는 대신 메모리에 파라미터를 캐싱하여 hot path 조회 성능을 높입니다.
 * 캐시 갱신은 항상 새 스냅샷을 만든 뒤 CAS로 교체하여 읽기 경로가 부분 상태를 보지 않도록 보장합니다.
 * </p>
 *
 * <p>파라미터 우선순위: EQP 파라미터 > Model 파라미터</p>
 */
@Component
public class BusinessModelParamCache implements BusinessModelParamProvider, BusinessModelParamMutationPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessModelParamCache.class);

    private final TcEqpStore eqpStore;
    private final TcModelParamStore modelParamStore;
    private final TcEqpParamStore eqpParamStore;
    private final BusinessModelRuntimeProvider runtimeProvider;
    private final BusinessModelCacheProperties cacheProperties;

    private final AtomicReference<BusinessModelParamSnapshot> snapshotRef =
            new AtomicReference<>(BusinessModelParamSnapshot.empty());

    /**
     * 캐시 구성 요소를 주입받습니다.
     */
    public BusinessModelParamCache(
            final TcEqpStore eqpStore,
            final TcModelParamStore modelParamStore,
            final TcEqpParamStore eqpParamStore,
            final BusinessModelRuntimeProvider runtimeProvider,
            final BusinessModelCacheProperties cacheProperties
    ) {
        this.eqpStore = Objects.requireNonNull(eqpStore, "eqpStore is null");
        this.modelParamStore = Objects.requireNonNull(modelParamStore, "modelParamStore is null");
        this.eqpParamStore = Objects.requireNonNull(eqpParamStore, "eqpParamStore is null");
        this.runtimeProvider = Objects.requireNonNull(runtimeProvider, "runtimeProvider is null");
        this.cacheProperties = Objects.requireNonNull(cacheProperties, "cacheProperties is null");
    }

    /**
     * 애플리케이션 기동 시 초기 파라미터 캐시를 로드합니다.
     *
     * <p>BusinessModelRuntimeCache(@PostConstruct)가 먼저 초기화되어야
     * eqpKey/modelVersionKey 매핑을 사용할 수 있습니다.</p>
     */
    @PostConstruct
    public void initialize() {
        if (!cacheProperties.isLoadOnStartup()) {
            log.info("Business model param cache bootstrap skipped. loadOnStartup=false");
            return;
        }

        try {
            reloadAll();
        } catch (RuntimeException ex) {
            log.error("Business model param cache bootstrap failed.", ex);
            if (cacheProperties.isFailFastOnStartup()) {
                throw ex;
            }
        }
    }

    /**
     * 전체 model/eqp 파라미터 스냅샷을 다시 적재합니다.
     */
    public void reloadAll() {
        final List<TcEqp> allEqps = loadAllEqps();

        final Map<Long, Map<String, String>> modelParams = loadAllModelParams(allEqps);
        final Map<Long, Map<String, String>> eqpParams = loadAllEqpParams(allEqps);

        final BusinessModelParamSnapshot nextSnapshot = BusinessModelParamSnapshot.of(modelParams, eqpParams);
        snapshotRef.set(nextSnapshot);

        log.info("Business model param cache reloaded. modelVersionCount={}, eqpCount={}",
                nextSnapshot.modelParamCount(),
                nextSnapshot.eqpParamCount());
    }

    /**
     * 현재 파라미터 스냅샷을 반환합니다.
     */
    @Override
    public BusinessModelParamSnapshot currentParamSnapshot() {
        return snapshotRef.get();
    }

    /**
     * eqpId와 paramName으로 파라미터 값을 조회합니다 (EQP 우선).
     */
    @Override
    public Optional<String> findParam(final String eqpId, final String paramName) {
        final Optional<Long> eqpKey = runtimeProvider.findEqpKeyByEqpId(eqpId);
        final Optional<Long> modelVersionKey = runtimeProvider.findModelVersionKeyByEqpId(eqpId);

        if (eqpKey.isEmpty() || modelVersionKey.isEmpty()) {
            log.warn("Cannot find param because eqpId is not registered. eqpId={}, paramName={}", eqpId, paramName);
            return Optional.empty();
        }

        return snapshotRef.get().resolveParam(eqpKey.get(), modelVersionKey.get(), paramName);
    }

    /**
     * eqpId 기준으로 EQP + Model 파라미터를 병합하여 반환합니다 (EQP 우선).
     */
    @Override
    public Map<String, String> findAllParams(final String eqpId) {
        final Optional<Long> eqpKey = runtimeProvider.findEqpKeyByEqpId(eqpId);
        final Optional<Long> modelVersionKey = runtimeProvider.findModelVersionKeyByEqpId(eqpId);

        if (eqpKey.isEmpty() || modelVersionKey.isEmpty()) {
            log.warn("Cannot find all params because eqpId is not registered. eqpId={}", eqpId);
            return Map.of();
        }

        return snapshotRef.get().findAllParams(eqpKey.get(), modelVersionKey.get());
    }

    /**
     * 특정 modelVersionKey의 Model 파라미터 맵을 반환합니다.
     */
    @Override
    public Map<String, String> findModelParams(final long modelVersionKey) {
        return snapshotRef.get().findModelParams(modelVersionKey);
    }

    /**
     * 특정 eqpId의 EQP 파라미터 맵을 반환합니다.
     */
    @Override
    public Map<String, String> findEqpParams(final String eqpId) {
        final Optional<Long> eqpKey = runtimeProvider.findEqpKeyByEqpId(eqpId);
        if (eqpKey.isEmpty()) {
            log.warn("Cannot find eqp params because eqpId is not registered. eqpId={}", eqpId);
            return Map.of();
        }
        return snapshotRef.get().findEqpParams(eqpKey.get());
    }

    /**
     * 특정 modelVersionKey의 파라미터 캐시를 DB에서 다시 적재합니다.
     *
     * <p>기존 스냅샷의 다른 엔트리는 유지하고 해당 key만 교체합니다.</p>
     */
    @Override
    public void reloadModelParams(final long modelVersionKey) {
        if (modelVersionKey <= 0L) {
            throw new IllegalArgumentException("modelVersionKey must be > 0");
        }

        final Map<String, String> newParams = loadModelParamsByVersionKey(modelVersionKey);

        while (true) {
            final BusinessModelParamSnapshot base = snapshotRef.get();
            final Map<Long, Map<String, String>> nextModelParams = base.copyAllModelParams();
            nextModelParams.put(modelVersionKey, newParams);

            final BusinessModelParamSnapshot next =
                    BusinessModelParamSnapshot.of(nextModelParams, base.copyAllEqpParams());

            if (snapshotRef.compareAndSet(base, next)) {
                log.info("Model params reloaded. modelVersionKey={}, paramCount={}", modelVersionKey, newParams.size());
                return;
            }
        }
    }

    /**
     * 특정 eqpKey의 파라미터 캐시를 DB에서 다시 적재합니다.
     *
     * <p>
     * eqpKey에 해당하는 설비의 appliedParamVersion을 DB에서 조회하여
     * 현재 적용된 버전의 파라미터를 로드합니다.
     * 기존 스냅샷의 다른 엔트리는 유지하고 해당 key만 교체합니다.
     * </p>
     */
    @Override
    public void reloadEqpParams(final long eqpKey) {
        if (eqpKey <= 0L) {
            throw new IllegalArgumentException("eqpKey must be > 0");
        }

        // DB에서 해당 설비의 현재 적용 버전 조회
        final Optional<TcEqp> eqpOpt = eqpStore.findByEqpId(resolveEqpId(eqpKey));
        if (eqpOpt.isEmpty()) {
            log.warn("Cannot reload eqp params because eqp is not found. eqpKey={}", eqpKey);
            return;
        }

        final Map<String, String> newParams = loadEqpParamsByEqp(eqpOpt.get());

        while (true) {
            final BusinessModelParamSnapshot base = snapshotRef.get();
            final Map<Long, Map<String, String>> nextEqpParams = base.copyAllEqpParams();
            nextEqpParams.put(eqpKey, newParams);

            final BusinessModelParamSnapshot next =
                    BusinessModelParamSnapshot.of(base.copyAllModelParams(), nextEqpParams);

            if (snapshotRef.compareAndSet(base, next)) {
                log.info("Eqp params reloaded. eqpKey={}, paramCount={}", eqpKey, newParams.size());
                return;
            }
        }
    }

    // ─── 내부 로딩 헬퍼 ──────────────────────────────────────────────────────

    /**
     * 모든 설비 목록을 페이지 단위로 조회합니다.
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
     * 모든 설비에 연결된 modelVersionKey 기준으로 model 파라미터를 로드합니다.
     */
    private Map<Long, Map<String, String>> loadAllModelParams(final List<TcEqp> allEqps) {
        final Set<Long> modelVersionKeys = new LinkedHashSet<>();
        for (TcEqp eqp : allEqps) {
            if (eqp != null && eqp.modelVersionKey() > 0L) {
                modelVersionKeys.add(eqp.modelVersionKey());
            }
        }

        final Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (Long modelVersionKey : modelVersionKeys) {
            result.put(modelVersionKey, loadModelParamsByVersionKey(modelVersionKey));
        }
        return result;
    }

    /**
     * 특정 modelVersionKey의 파라미터를 DB에서 페이지 단위로 로드합니다.
     */
    private Map<String, String> loadModelParamsByVersionKey(final long modelVersionKey) {
        final Map<String, String> params = new LinkedHashMap<>();
        final int pageSize = cacheProperties.getPageSize();
        int offset = 0;

        while (true) {
            final List<TcModelParam> page =
                    modelParamStore.findAllByModelVersionKey(modelVersionKey, PageRequest.of(offset, pageSize));
            if (page == null || page.isEmpty()) {
                break;
            }
            for (TcModelParam param : page) {
                params.put(param.paramName(), param.paramValue());
            }
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return params;
    }

    /**
     * 모든 설비의 EQP 파라미터를 appliedParamVersion 기준으로 로드합니다.
     */
    private Map<Long, Map<String, String>> loadAllEqpParams(final List<TcEqp> allEqps) {
        final Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (TcEqp eqp : allEqps) {
            if (eqp == null || eqp.eqpKey() <= 0L) {
                continue;
            }
            result.put(eqp.eqpKey(), loadEqpParamsByEqp(eqp));
        }
        return result;
    }

    /**
     * 특정 설비의 파라미터를 appliedParamVersion 기준으로 DB에서 로드합니다.
     */
    private Map<String, String> loadEqpParamsByEqp(final TcEqp eqp) {
        final String appliedVersion = eqp.appliedParamVersion();
        if (appliedVersion == null || appliedVersion.isBlank()) {
            // 적용된 파라미터 버전이 없으면 빈 맵 반환
            return Map.of();
        }

        final List<TcEqpParam> eqpParamList =
                eqpParamStore.findAllByEqpKeyAndVersion(eqp.eqpKey(), appliedVersion);

        final Map<String, String> params = new LinkedHashMap<>();
        if (eqpParamList != null) {
            for (TcEqpParam param : eqpParamList) {
                params.put(param.paramName(), param.paramValue());
            }
        }
        return params;
    }

    /**
     * eqpKey로 eqpId를 역조회합니다.
     *
     * <p>
     * BusinessModelRuntimeSnapshot의 eqpKeyBindings를 역방향으로 탐색합니다.
     * reloadEqpParams에서만 사용하며, 호출 빈도가 낮아 선형 탐색이 허용됩니다.
     * </p>
     *
     * @param eqpKey 조회할 설비 DB PK
     * @return eqpId(nullable)
     */
    private String resolveEqpId(final long eqpKey) {
        return runtimeProvider.currentSnapshot().eqpKeyBindings().entrySet().stream()
                .filter(e -> e.getValue().equals(eqpKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
