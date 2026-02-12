package com.nori.tc.comm.hsms.secs;

import com.nori.tc.comm.hsms.frame.HsmsFrame;
import com.nori.tc.comm.hsms.frame.HsmsHeader;
import com.nori.tc.comm.hsms.frame.HsmsPType;
import com.nori.tc.comm.hsms.frame.HsmsSType;

/**
 * SECS-II 디코더(기본 구현, 최소 기능)
 *
 * 동작
 * - HSMS 헤더의 stream/function/wBit를 추출하여 Secs2Message로 반환
 * - body는 raw bytes 그대로 유지
 *
 * 주의
 * - 이 구현은 SECS-II 타입 구조를 해석하지 않습니다.
 * - 추후 tc_model 기반 파싱 또는 스크립트 기반 파싱으로 확장할 수 있습니다.
 */
public final class BasicSecs2Decoder implements Secs2Decoder {

    @Override
    public Secs2Message decode(final HsmsFrame dataFrame) {
        if (dataFrame == null) throw new IllegalArgumentException("dataFrame is null");

        final HsmsHeader h = dataFrame.header();

        // 방어적 검증: DATA frame이어야 함
        if (h.sType() != HsmsSType.DATA || h.pType() != HsmsPType.SECS_II) {
            throw new IllegalArgumentException("Not a SECS-II DATA frame");
        }

        return new Secs2Message(
                h.stream(),
                h.function(),
                h.wBit(),
                dataFrame.body()
        );
    }
}
