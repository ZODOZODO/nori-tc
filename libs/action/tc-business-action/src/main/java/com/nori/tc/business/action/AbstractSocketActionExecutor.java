package com.nori.tc.business.action;

/**
 * SOCKET 계열 액션 실행기의 공통 추상 기반 클래스입니다.
 *
 * <p>이 타입을 상속한 구현 클래스는 레지스트리 빌드 단계에서
 * SOCKET 메시지 타입으로 분류됩니다.</p>
 *
 * <p>플러그인 JAR 개발 시 사용 예시:</p>
 * <pre>
 * public class MyEqpSocketHandler extends AbstractSocketActionExecutor {
 *
 *     {@literal @}TcAction("EVT")
 *     public void handleEvt(TcActionContext context) {
 *         String eqpId = context.eqpId();
 *         // SOCKET EVT 메시지 처리
 *     }
 * }
 * </pre>
 */
public abstract class AbstractSocketActionExecutor {
}
