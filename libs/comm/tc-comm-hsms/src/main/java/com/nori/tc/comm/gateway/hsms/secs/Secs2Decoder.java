package com.nori.tc.comm.gateway.hsms.secs;

import com.nori.tc.comm.gateway.hsms.frame.HsmsFrame;

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

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param dataFrame 처리할 원본 데이터
     * @return HSMS 통신 모듈 처리 결과
     */
    Secs2Message decode(HsmsFrame dataFrame);
}
