package com.nori.tc.comm.socket.frame;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;

/**
 * SOCKET 프레임 추출기(추상)
 *
 * 배경
 * - SOCKET은 프로토콜이 다양하므로 "하나의 추출기"로 통일하기 어렵습니다.
 * - 따라서 socketTypeHandler가 프레임 추출까지 담당하는 구조로 갑니다.
 *
 * 이 클래스는
 * - “프레임 추출이 필요하다”는 개념을 명확히 하기 위한 인터페이스 역할만 합니다.
 */
public interface SocketFrameExtractor {

    /**
     * reassemblyBuffer에서 프레임 1개를 추출합니다.
     *
     * @param buffer reassembly buffer
     * @return 추출 성공 시 frame, 아직 데이터가 부족하면 null
     */
    SocketFrame tryExtractOne(ReassemblyBuffer buffer);
}
