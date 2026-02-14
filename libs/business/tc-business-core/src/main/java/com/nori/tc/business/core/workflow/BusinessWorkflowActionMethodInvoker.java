package com.nori.tc.business.core.workflow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 액션 메서드 반사 호출 래퍼입니다.
 *
 * <p>어노테이션 기반으로 탐지된 메서드를 안전하게 실행하고,
 * 예외를 {@link BusinessWorkflowActionExecutionException}으로 표준화합니다.</p>
 */
public final class BusinessWorkflowActionMethodInvoker {

    private final Object targetInstance;
    private final Method method;
    private final BusinessWorkflowActionKey actionKey;
    private final boolean requiresContextParameter;

    /**
     * 액션 메서드 호출기 생성자입니다.
     *
     * @param targetInstance 메서드를 소유한 인스턴스
     * @param method 호출 대상 메서드
     * @param actionKey 레지스트리 키
     * @param requiresContextParameter 컨텍스트 파라미터 전달 필요 여부
     */
    public BusinessWorkflowActionMethodInvoker(
            final Object targetInstance,
            final Method method,
            final BusinessWorkflowActionKey actionKey,
            final boolean requiresContextParameter
    ) {
        this.targetInstance = Objects.requireNonNull(targetInstance, "targetInstance is null");
        this.method = Objects.requireNonNull(method, "method is null");
        this.actionKey = Objects.requireNonNull(actionKey, "actionKey is null");
        this.requiresContextParameter = requiresContextParameter;
        this.method.setAccessible(true);
    }

    /**
     * 액션 메서드를 실행합니다.
     *
     * @param context action context
     */
    public void invoke(final BusinessWorkflowActionContext context) {
        Objects.requireNonNull(context, "context is null");
        try {
            if (requiresContextParameter) {
                method.invoke(targetInstance, context);
            } else {
                method.invoke(targetInstance);
            }
        } catch (IllegalAccessException ex) {
            throw new BusinessWorkflowActionExecutionException(
                    "Action method access failed. key=" + actionKey + ", method=" + describeMethod(),
                    ex
            );
        } catch (InvocationTargetException ex) {
            final Throwable cause = ex.getTargetException() == null ? ex : ex.getTargetException();
            throw new BusinessWorkflowActionExecutionException(
                    "Action method execution failed. key=" + actionKey + ", method=" + describeMethod(),
                    cause
            );
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessWorkflowActionExecutionException(
                    "Unexpected action invocation failure. key=" + actionKey + ", method=" + describeMethod(),
                    ex
            );
        }
    }

    /**
     * 디버그/오류 로그 용도로 액션 메서드 설명 문자열을 반환합니다.
     *
     * @return ownerClass#methodName
     */
    public String describeMethod() {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    /**
     * 액션 키를 반환합니다.
     *
     * @return action key
     */
    public BusinessWorkflowActionKey actionKey() {
        return actionKey;
    }
}


