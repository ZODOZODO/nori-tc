package com.nori.tc.business.core.workflow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 액션 레지스트리 불변 스냅샷입니다.
 *
 * <p>key는 {@link BusinessWorkflowActionKey}, value는 실행기
 * {@link BusinessWorkflowActionMethodInvoker}로 구성됩니다.</p>
 */
public final class BusinessWorkflowActionRegistry {

    private static final BusinessWorkflowActionRegistry EMPTY =
            new BusinessWorkflowActionRegistry(Map.of());

    private final Map<BusinessWorkflowActionKey, BusinessWorkflowActionMethodInvoker> invokers;

    private BusinessWorkflowActionRegistry(
            final Map<BusinessWorkflowActionKey, BusinessWorkflowActionMethodInvoker> invokers
    ) {
        this.invokers = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(invokers, "invokers is null")));
    }

    /**
     * 주어진 매핑으로 불변 레지스트리를 생성합니다.
     *
     * @param invokers key -> invoker map
     * @return immutable registry
     */
    public static BusinessWorkflowActionRegistry of(
            final Map<BusinessWorkflowActionKey, BusinessWorkflowActionMethodInvoker> invokers
    ) {
        return new BusinessWorkflowActionRegistry(invokers);
    }

    /**
     * 빈 레지스트리를 반환합니다.
     *
     * @return empty registry
     */
    public static BusinessWorkflowActionRegistry empty() {
        return EMPTY;
    }

    /**
     * key로 액션 실행기를 조회합니다.
     *
     * @param key lookup key
     * @return invoker(optional)
     */
    public Optional<BusinessWorkflowActionMethodInvoker> find(final BusinessWorkflowActionKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(invokers.get(key));
    }

    /**
     * 등록된 액션 개수를 반환합니다.
     *
     * @return action count
     */
    public int size() {
        return invokers.size();
    }

    /**
     * 등록된 key 집합을 반환합니다.
     *
     * @return immutable key set
     */
    public Set<BusinessWorkflowActionKey> keys() {
        return invokers.keySet();
    }
}


