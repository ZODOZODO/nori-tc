package com.nori.tc.business.core.workflow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 워크플로우 액션 메서드를 등록할 때 사용하는 어노테이션입니다.
 *
 * <p>핵심 규칙:</p>
 * <p>1) key는 {@code (MessageType, action_name)} 조합으로 구성합니다.</p>
 * <p>2) {@code action_name}은 이 어노테이션의 {@link #value()}로 지정합니다.</p>
 * <p>3) MessageType은 메서드를 담고 있는 Executor 타입(SECS/SOCKET/MES)에서 결정합니다.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TcAction {

    /**
     * workflow row의 {@code action_name} 값입니다.
     *
     * @return action name
     */
    String value();
}


