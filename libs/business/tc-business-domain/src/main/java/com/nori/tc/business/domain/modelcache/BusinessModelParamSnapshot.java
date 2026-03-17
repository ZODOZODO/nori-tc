package com.nori.tc.business.domain.modelcache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Business model/eqp 파라미터 캐시의 불변 스냅샷입니다.
 *
 * <p>
 * 파라미터 우선순위: EQP 파라미터 > Model 파라미터.
 * 동일한 paramName이 양쪽에 모두 존재할 경우 EQP 파라미터 값이 사용됩니다.
 * </p>
 *
 * <p>원칙:
 * - 스냅샷 객체는 생성 이후 변경되지 않습니다.
 * - 캐시 갱신은 새 스냅샷을 조립한 뒤 atomic swap으로 교체합니다.</p>
 */
public final class BusinessModelParamSnapshot {

    private static final BusinessModelParamSnapshot EMPTY =
            new BusinessModelParamSnapshot(Map.of(), Map.of());

    /** modelVersionKey → (paramName → paramValue) */
    private final Map<Long, Map<String, String>> modelParams;

    /** eqpKey → (paramName → paramValue) */
    private final Map<Long, Map<String, String>> eqpParams;

    private BusinessModelParamSnapshot(
            final Map<Long, Map<String, String>> modelParams,
            final Map<Long, Map<String, String>> eqpParams
    ) {
        this.modelParams = copyDeep(Objects.requireNonNull(modelParams, "modelParams is null"));
        this.eqpParams = copyDeep(Objects.requireNonNull(eqpParams, "eqpParams is null"));
    }

    /**
     * 신규 스냅샷을 생성합니다.
     *
     * @param modelParams modelVersionKey → (paramName → paramValue) 매핑
     * @param eqpParams   eqpKey → (paramName → paramValue) 매핑
     * @return 불변 스냅샷
     */
    public static BusinessModelParamSnapshot of(
            final Map<Long, Map<String, String>> modelParams,
            final Map<Long, Map<String, String>> eqpParams
    ) {
        return new BusinessModelParamSnapshot(modelParams, eqpParams);
    }

    /**
     * 빈 스냅샷을 반환합니다.
     *
     * @return 빈 스냅샷
     */
    public static BusinessModelParamSnapshot empty() {
        return EMPTY;
    }

    /**
     * eqpKey와 modelVersionKey를 기준으로 특정 paramName의 값을 조회합니다.
     *
     * <p>EQP 파라미터가 존재하면 EQP 값을, 없으면 Model 파라미터 값을 반환합니다.</p>
     *
     * @param eqpKey          설비 DB PK
     * @param modelVersionKey 모델 버전 키
     * @param paramName       파라미터 이름
     * @return 파라미터 값(optional)
     */
    public Optional<String> resolveParam(final long eqpKey, final long modelVersionKey, final String paramName) {
        if (paramName == null || paramName.isBlank()) {
            return Optional.empty();
        }

        // EQP 파라미터 우선 조회
        final Map<String, String> eqpParamMap = eqpParams.getOrDefault(eqpKey, Map.of());
        final String eqpValue = eqpParamMap.get(paramName);
        if (eqpValue != null) {
            return Optional.of(eqpValue);
        }

        // Model 파라미터 fallback
        final Map<String, String> modelParamMap = modelParams.getOrDefault(modelVersionKey, Map.of());
        return Optional.ofNullable(modelParamMap.get(paramName));
    }

    /**
     * eqpKey와 modelVersionKey를 기준으로 병합된 파라미터 맵을 반환합니다.
     *
     * <p>Model 파라미터를 기본값으로 하고 EQP 파라미터로 덮어씁니다 (EQP 우선).</p>
     *
     * @param eqpKey          설비 DB PK
     * @param modelVersionKey 모델 버전 키
     * @return 병합된 파라미터 맵 (불변)
     */
    public Map<String, String> findAllParams(final long eqpKey, final long modelVersionKey) {
        final Map<String, String> merged = new LinkedHashMap<>(
                modelParams.getOrDefault(modelVersionKey, Map.of())
        );
        // EQP 파라미터로 덮어쓰기 (동일 paramName이면 EQP 값이 우선)
        merged.putAll(eqpParams.getOrDefault(eqpKey, Map.of()));
        return Collections.unmodifiableMap(merged);
    }

    /**
     * 특정 modelVersionKey의 파라미터 맵을 반환합니다.
     *
     * @param modelVersionKey 모델 버전 키
     * @return 파라미터 맵 (불변)
     */
    public Map<String, String> findModelParams(final long modelVersionKey) {
        return modelParams.getOrDefault(modelVersionKey, Map.of());
    }

    /**
     * 특정 eqpKey의 파라미터 맵을 반환합니다.
     *
     * @param eqpKey 설비 DB PK
     * @return 파라미터 맵 (불변)
     */
    public Map<String, String> findEqpParams(final long eqpKey) {
        return eqpParams.getOrDefault(eqpKey, Map.of());
    }

    /**
     * 캐시된 model 버전 수를 반환합니다.
     *
     * @return model 버전 수
     */
    public int modelParamCount() {
        return modelParams.size();
    }

    /**
     * 캐시된 설비 파라미터 수를 반환합니다.
     *
     * @return 설비 파라미터 수
     */
    public int eqpParamCount() {
        return eqpParams.size();
    }

    /**
     * model 파라미터 전체 맵의 가변 복사본을 반환합니다.
     *
     * <p>CAS 방식으로 스냅샷을 교체할 때 기존 엔트리를 재사용하기 위해 제공합니다.</p>
     *
     * @return modelVersionKey → (paramName → paramValue) 가변 복사본
     */
    public Map<Long, Map<String, String>> copyAllModelParams() {
        final Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, String>> entry : modelParams.entrySet()) {
            result.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return result;
    }

    /**
     * eqp 파라미터 전체 맵의 가변 복사본을 반환합니다.
     *
     * <p>CAS 방식으로 스냅샷을 교체할 때 기존 엔트리를 재사용하기 위해 제공합니다.</p>
     *
     * @return eqpKey → (paramName → paramValue) 가변 복사본
     */
    public Map<Long, Map<String, String>> copyAllEqpParams() {
        final Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, String>> entry : eqpParams.entrySet()) {
            result.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return result;
    }

    /**
     * 외부 맵을 불변 깊은 복사본으로 변환합니다.
     */
    private static Map<Long, Map<String, String>> copyDeep(final Map<Long, Map<String, String>> source) {
        final Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, String>> entry : source.entrySet()) {
            result.put(entry.getKey(), Map.copyOf(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }
}
