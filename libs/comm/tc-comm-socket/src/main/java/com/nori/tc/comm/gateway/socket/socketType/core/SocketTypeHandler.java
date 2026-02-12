package com.nori.tc.comm.gateway.socket.socketType.core;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.gateway.socket.frame.SocketFrame;

/**
 * socketType별 핸들러(핵심 계약)
 *
 * 목표
 * - socketType이 늘어나도, 각 타입의 encode/decode 로직이 “해당 디렉터리”에 모여 있게 합니다.
 * - tc-comm-gateway 유지보수성을 위해 가장 중요한 분리 지점입니다.
 *
 * 책임 범위
 * 1) 프레임 추출: reassemblyBuffer에서 프레임 1개를 뽑는다(없으면 null)
 * 2) decode: 추출된 프레임 bytes를 domain 메시지(메시지명 + body)로 변환한다
 * 3) encode(옵션): outbound command를 raw bytes로 인코딩한다
 */
public interface SocketTypeHandler {

    /**
     * 이 핸들러가 담당하는 socketType 문자열(예: "LINE_DELIMITED", "A", "B")
     */
    String socketType();

    /**
     * 프레임 1개 추출
     *
     * @param buffer reassembly buffer(누적된 수신 bytes)
     * @param maxFrameBytes 프레임 상한(폭주 방어)
     * @return 프레임 1개, 아직 부족하면 null
     */
    SocketFrame tryExtractOne(ReassemblyBuffer buffer, int maxFrameBytes);

    /**
     * 프레임 decode
     *
     * @param frameBytes 프레임 bytes
     * @return decode 결과(메시지명 + body 등)
     */
    SocketTypeDecodeResult decode(byte[] frameBytes);

    /**
     * outbound command encode(옵션)
     *
     * - 실제로 socket outbound를 사용하지 않는다면, 구현에서 UnsupportedOperationException을 던져도 됩니다.
     */
    default SocketTypeEncodeResult encode(final Object command) {
        throw new UnsupportedOperationException("encode() is not supported for socketType=" + socketType());
    }
}
