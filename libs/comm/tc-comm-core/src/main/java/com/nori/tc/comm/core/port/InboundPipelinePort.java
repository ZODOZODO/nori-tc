package com.nori.tc.comm.core.port;

import com.nori.tc.comm.core.eqp.EquipmentRuntimeContext;
import com.nori.tc.comm.core.message.InboundProcessResult;

/**
 * inbound 파이프라인 Port
 *
 * 역할
 * - reassemblyBuffer에 누적된 bytes를 읽어 "완전한 프레임"을 추출하고,
 * - 프로토콜(HSMS/SOCKET) 및 socketType에 따라 파싱/변환하여 ParsedMessage로 내보냅니다.
 * - 필요 시 즉시 송신해야 할 OutboundRawFrame도 함께 생성하여 반환합니다.
 *
 * 구현 위치
 * - HSMS 전용 구현: tc-comm-hsms
 * - SOCKET 전용 구현: tc-comm-socket
 * - 통합 구현(분기): tc-comm-gateway-app에서 HSMS/SOCKET 구현체를 조합해도 됩니다.
 *
 * 주의
 * - core 모듈은 프로토콜 구현을 직접 포함하지 않습니다.
 */
public interface InboundPipelinePort {

    /**
     * 현재 reassemblyBuffer 상태에서 가능한 만큼 메시지를 "drain" 합니다.
     *
     * @param ctx eqp 런타임 컨텍스트(프로파일/버퍼/태그)
     * @return 처리 결과(메시지/아웃바운드 프레임)
     */
    InboundProcessResult drain(EquipmentRuntimeContext ctx);
}
