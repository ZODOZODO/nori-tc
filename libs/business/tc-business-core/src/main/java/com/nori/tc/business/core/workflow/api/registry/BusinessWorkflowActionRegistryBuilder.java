package com.nori.tc.business.core.workflow.api.registry;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.annotation.TcAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link TcAction} 기반 액션 레지스트리 빌더입니다.
 *
 * <p>Executor 인스턴스에서 액션 메서드를 수집하고,
 * 중복 키를 검증한 뒤 최종 불변 레지스트리를 생성합니다.</p>
 */
public final class BusinessWorkflowActionRegistryBuilder {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowActionRegistryBuilder.class);

    private final Map<BusinessWorkflowActionKey, BusinessWorkflowActionMethodInvoker> invokerMap =
            new LinkedHashMap<>();

    /**
     * Executor 인스턴스를 스캔해 액션 메서드를 등록합니다.
     *
     * @param executorInstance executor 인스턴스
     * @param messageType 액션 메시지 타입
     * @return this
     */
    public BusinessWorkflowActionRegistryBuilder registerExecutor(
            final Object executorInstance,
            final BusinessWorkflowActionMessageType messageType
    ) {
        Objects.requireNonNull(executorInstance, "executorInstance is null");
        Objects.requireNonNull(messageType, "messageType is null");

        int registeredCount = 0;
        Class<?> current = executorInstance.getClass();
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                final TcAction tcAction = method.getAnnotation(TcAction.class);
                if (tcAction == null) {
                    continue;
                }

                validateActionMethod(method, executorInstance.getClass());

                final BusinessWorkflowActionKey key = BusinessWorkflowActionKey.of(messageType, tcAction.value());
                final boolean requiresContext = method.getParameterCount() == 1;
                final BusinessWorkflowActionMethodInvoker invoker = new BusinessWorkflowActionMethodInvoker(
                        executorInstance,
                        method,
                        key,
                        requiresContext
                );

                final BusinessWorkflowActionMethodInvoker existing = invokerMap.putIfAbsent(key, invoker);
                if (existing != null) {
                    throw new IllegalStateException(
                            "Duplicate TcAction key detected. key=" + key
                                    + ", existingMethod=" + existing.describeMethod()
                                    + ", duplicateMethod=" + invoker.describeMethod()
                    );
                }
                registeredCount++;
            }
            current = current.getSuperclass();
        }

        if (log.isDebugEnabled()) {
            log.debug("Executor action scan completed. executorClass={}, messageType={}, registeredCount={}",
                    executorInstance.getClass().getName(),
                    messageType,
                    registeredCount);
        }
        return this;
    }

    /**
     * 누적된 매핑으로 최종 레지스트리를 생성합니다.
     *
     * @return 불변 액션 레지스트리
     */
    public BusinessWorkflowActionRegistry build() {
        return BusinessWorkflowActionRegistry.of(invokerMap);
    }

    /**
     * 액션 메서드 시그니처를 검증합니다.
     *
     * <p>허용 규칙:</p>
     * <p>1) static 금지</p>
     * <p>2) return type은 void</p>
     * <p>3) 파라미터는 0개 또는 1개(BusinessWorkflowActionContext)</p>
     */
    private static void validateActionMethod(final Method method, final Class<?> ownerClass) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException("TcAction method must not be static. method="
                    + ownerClass.getName() + "#" + method.getName());
        }
        if (method.getReturnType() != Void.TYPE) {
            throw new IllegalStateException("TcAction method must return void. method="
                    + ownerClass.getName() + "#" + method.getName());
        }
        if (method.getParameterCount() > 1) {
            throw new IllegalStateException("TcAction method supports 0 or 1 parameter only. method="
                    + ownerClass.getName() + "#" + method.getName());
        }
        if (method.getParameterCount() == 1
                && !BusinessWorkflowActionContext.class.isAssignableFrom(method.getParameterTypes()[0])) {
            throw new IllegalStateException("TcAction method parameter must be BusinessWorkflowActionContext. method="
                    + ownerClass.getName() + "#" + method.getName());
        }
    }
}