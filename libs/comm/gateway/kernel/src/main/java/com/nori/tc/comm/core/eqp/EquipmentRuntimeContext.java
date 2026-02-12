package com.nori.tc.comm.core.eqp;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.core.inbound.InboundQueue;

import java.util.Map;

/**
 * 설비 런타임 컨텍스트(동적 상태 + 핸들)
 *
 * 목적
 * - eqp별 순차 처리 루프에서 필요한 런타임 요소를 한 곳에서 접근하도록 묶습니다.
 * - 구현(Queue 종류, 버퍼 크기, 메트릭 태그 등)은 앱에서 주입/관리합니다.
 *
 * 포함해야 하는 것(최소)
 * - profile         : commInterfaceType/socketType 분기 정보
 * - inboundQueue    : Netty 채널에서 들어온 raw chunk가 쌓이는 곳(반드시 bounded)
 * - reassemblyBuffer: chunk를 누적하여 프레임 추출에 사용
 *
 * 주의
 * - Netty Channel 같은 기술 의존 타입을 여기 두지 않습니다.
 *   outbound send는 OutboundSenderPort가 eqpId로 찾아서 처리하도록 둡니다.
 */
public interface EquipmentRuntimeContext {

    EquipmentProfile profile();

    InboundQueue inboundQueue();

    ReassemblyBuffer reassemblyBuffer();

    /**
     * 운영/메트릭 태그(선택)
     * - 예: socketTypeVersion, publishPolicyVersion, instanceId 등
     * - null 금지: 빈 맵 반환을 권장합니다.
     */
    Map<String, String> tags();
}
