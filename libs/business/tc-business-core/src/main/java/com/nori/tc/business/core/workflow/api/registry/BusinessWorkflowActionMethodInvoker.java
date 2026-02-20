package com.nori.tc.business.core.workflow.api.registry;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionExecutionException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 리플렉션 기반 액션 메서드 호출 도우미입니다.
 */
public final class BusinessWorkflowActionMethodInvoker {

    private final Object targetInstance;
    private final Method method;
    private final BusinessWorkflowActionKey actionKey;
    private final boolean requiresContextParameter;

    /**
     * 호출기 생성자입니다.
     *
     * @param targetInstance 대상 인스턴스
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
     * @param context 액션 실행 컨텍스트
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
     * 로깅용 메서드 식별 문자열을 반환합니다.
     *
     * @return declaringClass#methodName
     */
    public String describeMethod() {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    /**
     * 액션 키를 반환합니다.
     *
     * @return 레지스트리 키
     */
    public BusinessWorkflowActionKey actionKey() {
        return actionKey;
    }
}