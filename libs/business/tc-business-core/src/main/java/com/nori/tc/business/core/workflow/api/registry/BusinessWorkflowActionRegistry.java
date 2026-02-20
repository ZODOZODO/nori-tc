package com.nori.tc.business.core.workflow.api.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 액션 키와 호출기를 보관하는 불변 레지스트리입니다.
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
     * 주어진 맵으로 레지스트리를 생성합니다.
     *
     * @param invokers key -> invoker 맵
     * @return 불변 레지스트리
     */
    public static BusinessWorkflowActionRegistry of(
            final Map<BusinessWorkflowActionKey, BusinessWorkflowActionMethodInvoker> invokers
    ) {
        return new BusinessWorkflowActionRegistry(invokers);
    }

    /**
     * 비어 있는 레지스트리를 반환합니다.
     *
     * @return empty 레지스트리
     */
    public static BusinessWorkflowActionRegistry empty() {
        return EMPTY;
    }

    /**
     * 키로 호출기를 조회합니다.
     *
     * @param key 조회 키
     * @return 호출기(optional)
     */
    public Optional<BusinessWorkflowActionMethodInvoker> find(final BusinessWorkflowActionKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(invokers.get(key));
    }

    /**
     * 등록된 액션 수를 반환합니다.
     */
    public int size() {
        return invokers.size();
    }

    /**
     * 등록된 키 집합을 반환합니다.
     */
    public Set<BusinessWorkflowActionKey> keys() {
        return invokers.keySet();
    }
}