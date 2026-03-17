package com.nori.tc.business.action;

/**
 * SECS(HSMS) 계열 액션 실행기의 공통 추상 기반 클래스입니다.
 *
 * <p>이 타입을 상속한 구현 클래스는 레지스트리 빌드 단계에서
 * SECS 메시지 타입으로 분류됩니다.</p>
 *
 * <p>플러그인 JAR 개발 시 사용 예시:</p>
 * <pre>
 * public class MyEqpSecsHandler extends AbstractSecsActionExecutor {
 *
 *     {@literal @}TcAction("S6F11")
 *     public void handleS6F11(TcActionContext context) {
 *         String eqpId = context.eqpId();
 *         // SECS S6F11 이벤트 처리
 *     }
 * }
 * </pre>
 */
public abstract class AbstractSecsActionExecutor {
}
