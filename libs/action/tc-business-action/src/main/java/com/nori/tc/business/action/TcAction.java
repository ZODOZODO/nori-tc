package com.nori.tc.business.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 워크플로우 액션 메서드를 선언하기 위한 어노테이션입니다.
 *
 * <p>매핑 키는 {@code (MessageType, action_name)} 조합으로 구성되며,
 * 이 어노테이션의 {@link #value()} 값이 action_name으로 사용됩니다.</p>
 *
 * <p>메서드 시그니처 규칙:</p>
 * <ul>
 *   <li>static 메서드에는 사용할 수 없습니다.</li>
 *   <li>반환 타입은 {@code void}이어야 합니다.</li>
 *   <li>파라미터는 0개이거나, {@link TcActionContext} 타입 1개만 허용됩니다.</li>
 * </ul>
 *
 * <p>사용 예시:</p>
 * <pre>
 * public class MyEqpActionExecutor extends AbstractSecsActionExecutor {
 *
 *     {@literal @}TcAction("MY_EVENT")
 *     public void handleMyEvent(TcActionContext context) {
 *         // 이벤트 처리 로직
 *     }
 *
 *     {@literal @}TcAction("OTHER_EVENT")
 *     public void handleOther() {
 *         // 컨텍스트 없이도 동작
 *     }
 * }
 * </pre>
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
