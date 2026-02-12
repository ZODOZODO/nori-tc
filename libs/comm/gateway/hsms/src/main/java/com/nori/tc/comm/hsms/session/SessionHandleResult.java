package com.nori.tc.comm.hsms.session;

import com.nori.tc.comm.hsms.frame.HsmsFrame;

import java.util.List;

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

    public static SessionHandleResult allowData() {
        return new SessionHandleResult(List.of(), true);
    }

    public static SessionHandleResult denyData() {
        return new SessionHandleResult(List.of(), false);
    }
}
