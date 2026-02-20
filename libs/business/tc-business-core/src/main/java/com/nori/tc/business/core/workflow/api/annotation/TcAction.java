package com.nori.tc.business.core.workflow.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 워크플로우 액션 메서드를 선언하기 위한 어노테이션입니다.
 *
 * <p>매핑 키는 {@code (MessageType, action_name)} 조합으로 구성되며,
 * 이 어노테이션의 {@link #value()} 값이 action_name으로 사용됩니다.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TcAction {

    /**
     * 워크플로우 액션 이름(action_name)을 지정합니다.
     *
     * @return 액션 이름
     */
    String value();
}