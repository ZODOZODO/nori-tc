package com.nori.tc.business.action;

import java.util.Map;

/**
 * Business 플러그인 JAR 개발자가 {@link TcAction} 메서드에서 사용하는 경량 실행 컨텍스트입니다.
 *
 * <p>이 인터페이스는 Spring/Kafka/Jackson 의존성 없이 순수 Java로만 구성되어 있어,
 * 플러그인 JAR 개발 시 {@code compileOnly}로 의존하기에 적합합니다.</p>
 *
 * <p>런타임에는 {@code tc-business-core}의 {@code BusinessWorkflowActionContext}가
 * 이 인터페이스를 구현하여 실제 값을 제공합니다.</p>
 *
 * <p>플러그인 액션 메서드 파라미터로 사용하는 예:</p>
 * <pre>
 * {@literal @}TcAction("MY_ACTION")
 * public void handleMyAction(TcActionContext context) {
 *     String eqpId = context.eqpId();
 *     String messageName = context.messageName();
 *     Map&lt;String, Object&gt; vars = context.messageVariables();
 *     // ...
 * }
 * </pre>
 */
public interface TcActionContext {

    /**
     * 이벤트를 발생시킨 설비 ID를 반환합니다.
     *
     * @return 설비 ID
     */
    String eqpId();

    /**
     * 수신된 메시지 이름(S6F11, EVT 등)을 반환합니다.
     *
     * @return 메시지 이름
     */
    String messageName();

    /**
     * 수신된 메시지 원본 payload 문자열을 반환합니다.
     *
     * @return 원본 payload (없으면 null)
     */
    String payload();

    /**
     * 현재 처리 흐름의 traceId를 반환합니다.
     *
     * @return traceId
     */
    String traceId();

    /**
     * 메시지 레벨 변수 맵(MSG 네임스페이스)을 반환합니다.
     *
     * <p>S6F11 CEID, 데이터 블록 등 이벤트에서 파싱된 값들이 담겨 있습니다.</p>
     *
     * @return 불변 메시지 변수 맵
     */
    Map<String, Object> messageVariables();

    /**
     * 컨텍스트 레벨 변수 맵(CTX 네임스페이스)을 반환합니다.
     *
     * <p>워크플로우 필터 평가 시 사용된 설비/모델 수준 변수들이 담겨 있습니다.</p>
     *
     * @return 불변 컨텍스트 변수 맵
     */
    Map<String, Object> contextVariables();

    /**
     * 현재 실행 중인 워크플로우 이름을 반환합니다.
     *
     * @return 워크플로우 이름
     */
    String workflowName();

    /**
     * 현재 실행 중인 액션 이름을 반환합니다.
     *
     * @return 액션 이름
     */
    String actionName();
}
