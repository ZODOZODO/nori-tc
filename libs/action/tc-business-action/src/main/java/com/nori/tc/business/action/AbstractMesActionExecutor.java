package com.nori.tc.business.action;

/**
 * MES 계열 액션 실행기의 공통 추상 기반 클래스입니다.
 *
 * <p>이 타입을 상속한 구현 클래스는 레지스트리 빌드 단계에서
 * MES 메시지 타입으로 분류됩니다.</p>
 *
 * <p>플러그인 JAR 개발 시 사용 예시:</p>
 * <pre>
 * public class MyEqpMesHandler extends AbstractMesActionExecutor {
 *
 *     {@literal @}TcAction("DATACOLL")
 *     public void handleDatacoll(TcActionContext context) {
 *         String eqpId = context.eqpId();
 *         // MES DATACOLL 이벤트 처리
 *     }
 * }
 * </pre>
 */
public abstract class AbstractMesActionExecutor {
}
