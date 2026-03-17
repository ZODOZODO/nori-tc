package com.nori.tc.business.domain.work;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * eqpId 단위의 in-memory work 컨텍스트.
 *
 * <p>
 * workId → WorkEntry 단일 맵으로 구성됩니다.
 * lotId/carrierId/portId 조회는 선형 탐색으로 수행하며,
 * 설비당 평균 work 건수(10~20건)에서는 이 방식이 충분히 효율적입니다.
 * </p>
 *
 * <p>불변 record로 설계되어 있으며, 변경 연산은 새 인스턴스를 반환합니다.</p>
 */
public record EqpWorkContext(Map<String, WorkEntry> entries) {

    private static final EqpWorkContext EMPTY = new EqpWorkContext(Map.of());

    /**
     * 빈 컨텍스트 싱글턴을 반환합니다.
     */
    public static EqpWorkContext empty() {
        return EMPTY;
    }

    /**
     * entries 맵을 방어적으로 복사하여 불변 컨텍스트를 생성합니다.
     */
    public EqpWorkContext {
        Objects.requireNonNull(entries, "entries is null");
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /**
     * workId로 단건 조회합니다.
     *
     * @param workId 조회할 work ID
     * @return WorkEntry (없으면 empty)
     */
    public Optional<WorkEntry> findByWorkId(final String workId) {
        if (workId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(workId));
    }

    /**
     * 이 컨텍스트의 모든 WorkEntry를 반환합니다.
     *
     * @return 불변 WorkEntry 목록
     */
    public List<WorkEntry> findAll() {
        return List.copyOf(entries.values());
    }

    /**
     * lotId가 포함된 첫 번째 WorkEntry를 반환합니다 (선형 탐색).
     *
     * @param lotId 조회할 lot ID
     * @return 첫 번째 매칭 WorkEntry (없으면 empty)
     */
    public Optional<WorkEntry> findByLotId(final String lotId) {
        for (WorkEntry entry : entries.values()) {
            if (entry.hasLotId(lotId)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * lotId가 포함된 모든 WorkEntry를 반환합니다 (선형 탐색).
     *
     * @param lotId 조회할 lot ID
     * @return 매칭 WorkEntry 목록 (없으면 빈 리스트)
     */
    public List<WorkEntry> findAllByLotId(final String lotId) {
        final List<WorkEntry> result = new ArrayList<>();
        for (WorkEntry entry : entries.values()) {
            if (entry.hasLotId(lotId)) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * carrierId가 포함된 첫 번째 WorkEntry를 반환합니다 (선형 탐색).
     *
     * @param carrierId 조회할 carrier ID
     * @return 첫 번째 매칭 WorkEntry (없으면 empty)
     */
    public Optional<WorkEntry> findByCarrierId(final String carrierId) {
        for (WorkEntry entry : entries.values()) {
            if (entry.hasCarrierId(carrierId)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * carrierId가 포함된 모든 WorkEntry를 반환합니다 (선형 탐색).
     *
     * @param carrierId 조회할 carrier ID
     * @return 매칭 WorkEntry 목록 (없으면 빈 리스트)
     */
    public List<WorkEntry> findAllByCarrierId(final String carrierId) {
        final List<WorkEntry> result = new ArrayList<>();
        for (WorkEntry entry : entries.values()) {
            if (entry.hasCarrierId(carrierId)) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * portId가 포함된 첫 번째 WorkEntry를 반환합니다 (선형 탐색).
     *
     * @param portId 조회할 port ID
     * @return 첫 번째 매칭 WorkEntry (없으면 empty)
     */
    public Optional<WorkEntry> findByPortId(final String portId) {
        for (WorkEntry entry : entries.values()) {
            if (entry.hasPortId(portId)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * portId가 포함된 모든 WorkEntry를 반환합니다 (선형 탐색).
     *
     * @param portId 조회할 port ID
     * @return 매칭 WorkEntry 목록 (없으면 빈 리스트)
     */
    public List<WorkEntry> findAllByPortId(final String portId) {
        final List<WorkEntry> result = new ArrayList<>();
        for (WorkEntry entry : entries.values()) {
            if (entry.hasPortId(portId)) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * WorkEntry를 추가하거나 교체한 새 컨텍스트를 반환합니다.
     *
     * <p>work.workId()를 키로 사용합니다.</p>
     *
     * @param entry 추가/교체할 WorkEntry
     * @return 새 EqpWorkContext
     */
    public EqpWorkContext put(final WorkEntry entry) {
        Objects.requireNonNull(entry, "entry is null");
        final Map<String, WorkEntry> next = new LinkedHashMap<>(entries);
        next.put(entry.work().workId(), entry);
        return new EqpWorkContext(next);
    }

    /**
     * workId에 해당하는 WorkEntry를 제거한 새 컨텍스트를 반환합니다.
     *
     * <p>해당 workId가 없으면 현재 인스턴스를 그대로 반환합니다.</p>
     *
     * @param workId 제거할 work ID
     * @return 새 EqpWorkContext (또는 변경 없으면 현재 인스턴스)
     */
    public EqpWorkContext remove(final String workId) {
        if (workId == null || !entries.containsKey(workId)) {
            return this;
        }
        final Map<String, WorkEntry> next = new LinkedHashMap<>(entries);
        next.remove(workId);
        return new EqpWorkContext(next);
    }

    /**
     * 컨텍스트에 등록된 work 건수를 반환합니다.
     */
    public int size() {
        return entries.size();
    }

    /**
     * 컨텍스트가 비어 있는지 반환합니다.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
