package com.nori.tc.business.domain.modelcache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Business model runtime 캐시의 불변 스냅샷입니다.
 *
 * <p>원칙:
 * - 스냅샷 객체는 생성 이후 변경되지 않습니다.
 * - cache 갱신은 새 스냅샷을 조립한 뒤 atomic swap으로 교체합니다.</p>
 */
public final class BusinessModelRuntimeSnapshot {

    private static final BusinessModelRuntimeSnapshot EMPTY = new BusinessModelRuntimeSnapshot(Map.of(), Map.of());

    private final Map<String, Long> eqpModelBindings;
    private final Map<Long, TcModelRuntime> modelRuntimes;

    private BusinessModelRuntimeSnapshot(
            final Map<String, Long> eqpModelBindings,
            final Map<Long, TcModelRuntime> modelRuntimes
    ) {
        this.eqpModelBindings = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(eqpModelBindings, "eqpModelBindings is null")));
        this.modelRuntimes = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(modelRuntimes, "modelRuntimes is null")));
    }

    /**
     * 신규 스냅샷을 생성합니다.
     *
     * @param eqpModelBindings eqpId -> modelKey 매핑
     * @param modelRuntimes modelKey -> runtime 매핑
     * @return 불변 스냅샷
     */
    public static BusinessModelRuntimeSnapshot of(
            final Map<String, Long> eqpModelBindings,
            final Map<Long, TcModelRuntime> modelRuntimes
    ) {
        return new BusinessModelRuntimeSnapshot(eqpModelBindings, modelRuntimes);
    }

    /**
     * 빈 스냅샷을 반환합니다.
     *
     * @return 빈 스냅샷
     */
    public static BusinessModelRuntimeSnapshot empty() {
        return EMPTY;
    }

    /**
     * eqpModelBindings 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Map<String, Long> eqpModelBindings() {
        return eqpModelBindings;
    }

    /**
     * modelRuntimes 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Map<Long, TcModelRuntime> modelRuntimes() {
        return modelRuntimes;
    }

    /**
     * eqpId로 modelKey를 조회합니다.
     *
     * @param eqpId 장비 ID
     * @return modelKey(optional)
     */
    public Optional<Long> findModelKeyByEqpId(final String eqpId) {
        final String normalized = normalizeEqpId(eqpId);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(eqpModelBindings.get(normalized));
    }

    /**
     * eqpId로 runtime을 조회합니다.
     *
     * @param eqpId 장비 ID
     * @return runtime(optional)
     */
    public Optional<TcModelRuntime> findRuntimeByEqpId(final String eqpId) {
        return findModelKeyByEqpId(eqpId).flatMap(this::findRuntimeByModelKey);
    }

    /**
     * modelKey로 runtime을 조회합니다.
     *
     * @param modelKey model key
     * @return runtime(optional)
     */
    public Optional<TcModelRuntime> findRuntimeByModelKey(final long modelKey) {
        return Optional.ofNullable(modelRuntimes.get(modelKey));
    }

    /**
     * bindingCount 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int bindingCount() {
        return eqpModelBindings.size();
    }

    /**
     * runtimeCount 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int runtimeCount() {
        return modelRuntimes.size();
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

