package com.nori.tc.comm.gateway.hsms.secs;

import com.nori.tc.comm.gateway.hsms.frame.HsmsFrame;
import com.nori.tc.comm.gateway.hsms.frame.HsmsHeader;
import com.nori.tc.comm.gateway.hsms.frame.HsmsPType;
import com.nori.tc.comm.gateway.hsms.frame.HsmsSType;

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

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param dataFrame 처리할 원본 데이터
     * @return HSMS 통신 모듈 처리 결과
     */
    @Override
    public Secs2Message decode(final HsmsFrame dataFrame) {
        // 파싱 단계: 입력 포맷을 해석해 필요한 필드만 안전하게 추출합니다.
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
