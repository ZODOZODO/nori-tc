package com.nori.tc.comm.gateway.hsms.pipeline;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.gateway.hsms.session.HsmsSessionStateMachine;

/**
 * HSMS 런타임 컨텍스트(확장 인터페이스)
 *
 * 이유
 * - tc-comm-core의 EquipmentRuntimeContext는 "공통"만 정의하고,
 *   프로토콜별 런타임 상태(HSMS 세션 머신 등)는 프로토콜 모듈에서 확장 인터페이스로 노출합니다.
 *
 * 앱 레이어 구현
 * - HSMS 설비의 ctx 구현체는 이 인터페이스를 구현해야 합니다.
 * - SOCKET 설비의 ctx는 다른 확장 인터페이스를 구현하게 될 것입니다.
 */
public interface HsmsRuntimeContext extends EquipmentRuntimeContext {

    /**
     * eqp별 HSMS 세션 상태 머신(연결/세션 단위로 유지)
     */
    HsmsSessionStateMachine hsmsSession();
}
