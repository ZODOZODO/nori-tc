package com.nori.tc.business.adapters.db.work;

import com.nori.tc.business.adapters.db.modelcache.BusinessModelCacheProperties;
import com.nori.tc.business.core.modelcache.BusinessModelRuntimeProvider;
import com.nori.tc.business.core.work.BusinessWorkMutationPort;
import com.nori.tc.business.core.work.BusinessWorkProvider;
import com.nori.tc.business.domain.work.EqpWorkContext;
import com.nori.tc.business.domain.work.WorkEntry;
import com.nori.tc.db.core.common.PageRequest;
import com.nori.tc.db.core.work.store.TcWorkCarrierStore;
import com.nori.tc.db.core.work.store.TcWorkControlJobStore;
import com.nori.tc.db.core.work.store.TcWorkLotStore;
import com.nori.tc.db.core.work.store.TcWorkProcessJobStore;
import com.nori.tc.db.core.work.store.TcWorkStore;
import com.nori.tc.db.domain.work.TcWork;
import com.nori.tc.db.domain.work.TcWorkCarrier;
import com.nori.tc.db.domain.work.TcWorkControlJob;
import com.nori.tc.db.domain.work.TcWorkLot;
import com.nori.tc.db.domain.work.TcWorkProcessJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * eqpId 단위 lazy in-memory work 캐시입니다.
 *
 * <p>
 * 첫 접근 시 DB에서 해당 eqpId의 work 전체를 로드하며(lazy loading),
 * 이후 접근은 메모리에서 바로 반환합니다.
 * work 변경(create/update/delete) 시 {@link BusinessWorkMutationPort}를 통해 캐시를 동기화합니다.
 * </p>
 *
 * <p>
 * Mailbox 패턴에 의해 같은 eqpId 메시지는 단일 스레드가 순차 처리하므로
 * 동일 eqpId에 대한 동시 쓰기는 발생하지 않습니다.
 * {@link ConcurrentHashMap#computeIfAbsent}를 통한 lazy load도 이 보장 하에 안전합니다.
 * </p>
 *
 * <p>캐시 키 부재(absent) = 미로드 상태. {@code remove()}로 무효화하면 다음 접근 시 DB reload됩니다.</p>
 */
@Component
public class BusinessWorkCache implements BusinessWorkProvider, BusinessWorkMutationPort {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkCache.class);

    private final TcWorkStore workStore;
    private final TcWorkLotStore workLotStore;
    private final TcWorkCarrierStore workCarrierStore;
    private final TcWorkControlJobStore workControlJobStore;
    private final TcWorkProcessJobStore workProcessJobStore;
    private final BusinessModelRuntimeProvider runtimeProvider;
    private final BusinessModelCacheProperties cacheProperties;

    /** eqpId → EqpWorkContext. 키 부재 = 아직 로드되지 않은 상태. */
    private final ConcurrentHashMap<String, EqpWorkContext> cache = new ConcurrentHashMap<>();

    /**
     * 캐시 구성 요소를 주입받습니다.
     */
    public BusinessWorkCache(
            final TcWorkStore workStore,
            final TcWorkLotStore workLotStore,
            final TcWorkCarrierStore workCarrierStore,
            final TcWorkControlJobStore workControlJobStore,
            final TcWorkProcessJobStore workProcessJobStore,
            final BusinessModelRuntimeProvider runtimeProvider,
            final BusinessModelCacheProperties cacheProperties
    ) {
        this.workStore = Objects.requireNonNull(workStore, "workStore is null");
        this.workLotStore = Objects.requireNonNull(workLotStore, "workLotStore is null");
        this.workCarrierStore = Objects.requireNonNull(workCarrierStore, "workCarrierStore is null");
        this.workControlJobStore = Objects.requireNonNull(workControlJobStore, "workControlJobStore is null");
        this.workProcessJobStore = Objects.requireNonNull(workProcessJobStore, "workProcessJobStore is null");
        this.runtimeProvider = Objects.requireNonNull(runtimeProvider, "runtimeProvider is null");
        this.cacheProperties = Objects.requireNonNull(cacheProperties, "cacheProperties is null");
    }

    // ─── BusinessWorkProvider ────────────────────────────────────────────────

    /**
     * eqpId 기준으로 첫 번째 WorkEntry를 반환합니다.
     */
    @Override
    public Optional<WorkEntry> findByEqpId(final String eqpId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            return Optional.empty();
        }
        return getOrLoad(normalized).findAll().stream().findFirst();
    }

    /**
     * eqpId 기준으로 모든 WorkEntry를 반환합니다.
     */
    @Override
    public List<WorkEntry> findAllByEqpId(final String eqpId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            return List.of();
        }
        return getOrLoad(normalized).findAll();
    }

    /**
     * eqpId + lotId 기준으로 첫 번째 WorkEntry를 반환합니다.
     */
    @Override
    public Optional<WorkEntry> findByEqpIdAndLotId(final String eqpId, final String lotId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            return Optional.empty();
        }
        return getOrLoad(normalized).findByLotId(lotId);
    }

    /**
     * eqpId + lotId 기준으로 모든 WorkEntry를 반환합니다.
     */
    @Override
    public List<WorkEntry> findAllByEqpIdAndLotId(final String eqpId, final String lotId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            return List.of();
        }
        return getOrLoad(normalized).findAllByLotId(lotId);
    }

    /**
     * eqpId + carrierId 기준으로 첫 번째 WorkEntry를 반환합니다.
     */
    @Override
    public Optional<WorkEntry> findByEqpIdAndCarrierId(final String eqpId, final String carrierId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            return Optional.empty();
        }
        return getOrLoad(normalized).findByCarrierId(carrierId);
    }

    /**
     * eqpId + carrierId 기준으로 모든 WorkEntry를 반환합니다.
     */
    @Override
    public List<WorkEntry> findAllByEqpIdAndCarrierId(final String eqpId, final String carrierId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            return List.of();
        }
        return getOrLoad(normalized).findAllByCarrierId(carrierId);
    }

    /**
     * eqpId + portId 기준으로 첫 번째 WorkEntry를 반환합니다.
     */
    @Override
    public Optional<WorkEntry> findByEqpIdAndPortId(final String eqpId, final String portId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            return Optional.empty();
        }
        return getOrLoad(normalized).findByPortId(portId);
    }

    /**
     * eqpId + portId 기준으로 모든 WorkEntry를 반환합니다.
     */
    @Override
    public List<WorkEntry> findAllByEqpIdAndPortId(final String eqpId, final String portId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            return List.of();
        }
        return getOrLoad(normalized).findAllByPortId(portId);
    }

    // ─── BusinessWorkMutationPort ────────────────────────────────────────────

    /**
     * work와 관련 서브 엔티티를 캐시에 동기화합니다.
     *
     * <p>
     * 캐시가 아직 로드되지 않은 상태(absent)라면 단건만 등록하지 않고
     * lazy 로드 시 전체를 DB에서 가져오도록 그냥 두어 일관성을 유지합니다.
     * 이미 로드된 상태라면 해당 workId 항목만 교체합니다.
     * </p>
     */
    @Override
    public void syncWork(
            final String eqpId,
            final TcWork work,
            final List<TcWorkLot> lots,
            final List<TcWorkCarrier> carriers,
            final TcWorkControlJob controlJob,
            final List<TcWorkProcessJob> processJobs
    ) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            log.warn("syncWork skipped because eqpId is blank.");
            return;
        }
        if (work == null) {
            log.warn("syncWork skipped because work is null. eqpId={}", normalized);
            return;
        }

        final WorkEntry entry = new WorkEntry(work, lots, carriers, controlJob, processJobs);

        // 이미 로드된 컨텍스트가 있을 때만 업데이트. 없으면 다음 접근 시 lazy load로 처리.
        cache.computeIfPresent(normalized, (key, existing) -> existing.put(entry));

        log.debug("Work cache synced. eqpId={}, workId={}", normalized, work.workId());
    }

    /**
     * 특정 workKey에 해당하는 work를 캐시에서 제거합니다.
     *
     * <p>eqpId 컨텍스트가 로드된 상태에서만 동작하며, 없으면 무시합니다.</p>
     */
    @Override
    public void evictWork(final String eqpId, final long workKey) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            log.warn("evictWork skipped because eqpId is blank.");
            return;
        }

        cache.computeIfPresent(normalized, (key, existing) -> {
            // workKey로 workId를 역조회하여 제거
            final String workId = existing.findAll().stream()
                    .filter(e -> e.work().workKey() == workKey)
                    .map(e -> e.work().workId())
                    .findFirst()
                    .orElse(null);

            if (workId == null) {
                log.debug("evictWork skipped because workKey is not found in cache. eqpId={}, workKey={}", normalized, workKey);
                return existing;
            }

            log.debug("Work evicted from cache. eqpId={}, workId={}, workKey={}", normalized, workId, workKey);
            return existing.remove(workId);
        });
    }

    /**
     * eqpId 전체 캐시를 무효화합니다.
     *
     * <p>무효화 이후 첫 접근 시 DB에서 lazy reload됩니다.</p>
     */
    @Override
    public void evictEqp(final String eqpId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            log.warn("evictEqp skipped because eqpId is blank.");
            return;
        }
        cache.remove(normalized);
        log.info("Eqp work cache evicted. eqpId={}", normalized);
    }

    // ─── 내부 로딩 헬퍼 ──────────────────────────────────────────────────────

    /**
     * 캐시에서 컨텍스트를 가져오거나, 없으면 DB에서 lazy 로드합니다.
     *
     * <p>
     * {@link ConcurrentHashMap#computeIfAbsent}를 사용하므로,
     * 동일 키에 대한 동시 요청이 있더라도 DB 로드는 한 번만 수행됩니다.
     * Mailbox 패턴으로 같은 eqpId는 단일 스레드가 처리하므로 실제 경합은 드뭅니다.
     * </p>
     */
    private EqpWorkContext getOrLoad(final String eqpId) {
        return cache.computeIfAbsent(eqpId, this::loadContext);
    }

    /**
     * DB에서 해당 eqpId의 전체 work 컨텍스트를 로드합니다.
     *
     * <p>eqpKey를 runtimeProvider를 통해 해석한 뒤 페이지 단위로 work를 조회합니다.</p>
     */
    private EqpWorkContext loadContext(final String eqpId) {
        final Optional<Long> eqpKeyOpt = runtimeProvider.findEqpKeyByEqpId(eqpId);
        if (eqpKeyOpt.isEmpty()) {
            log.warn("Cannot load work context because eqpKey is not registered. eqpId={}", eqpId);
            return EqpWorkContext.empty();
        }

        final long eqpKey = eqpKeyOpt.get();
        final Map<String, WorkEntry> entries = new LinkedHashMap<>();
        final int pageSize = cacheProperties.getPageSize();
        int offset = 0;

        while (true) {
            final List<TcWork> page = workStore.findAllByEqpKey(eqpKey, PageRequest.of(offset, pageSize));
            if (page == null || page.isEmpty()) {
                break;
            }
            for (TcWork work : page) {
                final WorkEntry entry = loadWorkEntry(work);
                entries.put(work.workId(), entry);
            }
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }

        log.info("Work context loaded from DB. eqpId={}, workCount={}", eqpId, entries.size());
        return new EqpWorkContext(entries);
    }

    /**
     * 단일 work에 대한 lot, carrier, controlJob, processJob을 로드하여 WorkEntry를 구성합니다.
     */
    private WorkEntry loadWorkEntry(final TcWork work) {
        final long workKey = work.workKey();
        final int pageSize = cacheProperties.getPageSize();

        final List<TcWorkLot> lots = loadAllLots(workKey, pageSize);
        final List<TcWorkCarrier> carriers = loadAllCarriers(workKey, pageSize);

        // work당 controlJob은 첫 번째 한 건만 사용
        final List<TcWorkControlJob> controlJobs =
                workControlJobStore.findAllByWorkKey(workKey, PageRequest.of(0, 1));
        final TcWorkControlJob controlJob =
                (controlJobs != null && !controlJobs.isEmpty()) ? controlJobs.get(0) : null;

        final List<TcWorkProcessJob> processJobs;
        if (controlJob != null) {
            processJobs = loadAllProcessJobs(controlJob.controlJobKey(), pageSize);
        } else {
            processJobs = List.of();
        }

        return new WorkEntry(work, lots, carriers, controlJob, processJobs);
    }

    /**
     * workKey 기준으로 모든 lot을 페이지 단위로 로드합니다.
     */
    private List<TcWorkLot> loadAllLots(final long workKey, final int pageSize) {
        final List<TcWorkLot> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            final List<TcWorkLot> page = workLotStore.findAllByWorkKey(workKey, PageRequest.of(offset, pageSize));
            if (page == null || page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return result;
    }

    /**
     * workKey 기준으로 모든 carrier를 페이지 단위로 로드합니다.
     */
    private List<TcWorkCarrier> loadAllCarriers(final long workKey, final int pageSize) {
        final List<TcWorkCarrier> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            final List<TcWorkCarrier> page = workCarrierStore.findAllByWorkKey(workKey, PageRequest.of(offset, pageSize));
            if (page == null || page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return result;
    }

    /**
     * controlJobKey 기준으로 모든 processJob을 페이지 단위로 로드합니다.
     */
    private List<TcWorkProcessJob> loadAllProcessJobs(final long controlJobKey, final int pageSize) {
        final List<TcWorkProcessJob> result = new ArrayList<>();
        int offset = 0;
        while (true) {
            final List<TcWorkProcessJob> page =
                    workProcessJobStore.findAllByControlJobKey(controlJobKey, PageRequest.of(offset, pageSize));
            if (page == null || page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        return result;
    }

    /**
     * eqpId를 trim하고 빈 문자열이면 null을 반환합니다.
     */
    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null) {
            return null;
        }
        final String normalized = eqpId.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
