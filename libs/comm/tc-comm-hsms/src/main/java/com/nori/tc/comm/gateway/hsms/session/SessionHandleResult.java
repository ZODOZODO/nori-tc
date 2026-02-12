package com.nori.tc.comm.gateway.hsms.session;

import java.util.List;

import com.nori.tc.comm.gateway.hsms.frame.HsmsFrame;

/**
 * 세션 머신이 inbound frame을 처리한 결과
 *
 * - outboundControlFrames: 응답/제어 프레임(SELECT_RSP, LINKTEST_RSP 등)
 * - allowDataProcessing  : 이 프레임이 DATA일 때 파싱/변환을 진행해도 되는지 여부
 */
public record SessionHandleResult(
        List<HsmsFrame> outboundControlFrames,
        boolean allowDataProcessing
) {
    public SessionHandleResult {
        if (outboundControlFrames == null) outboundControlFrames = List.of();
    }

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @return HSMS 통신 모듈 처리 결과
     */
    public static SessionHandleResult allowData() {
        return new SessionHandleResult(List.of(), true);
    }

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @return HSMS 통신 모듈 처리 결과
     */
    public static SessionHandleResult denyData() {
        return new SessionHandleResult(List.of(), false);
    }
}
