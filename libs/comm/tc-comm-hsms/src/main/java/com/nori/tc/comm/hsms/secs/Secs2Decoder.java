package com.nori.tc.comm.hsms.secs;

import com.nori.tc.comm.hsms.frame.HsmsFrame;

/**
 * SECS-II 디코더 인터페이스
 *
 * 입력
 * - HSMS DATA frame(pType=0, sType=DATA)
 *
 * 출력
 * - 최소 모델(Secs2Message)
 */
public interface Secs2Decoder {

    Secs2Message decode(HsmsFrame dataFrame);
}
