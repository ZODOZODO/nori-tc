package com.nori.tc.comm.gateway.hsms.frame;

/**
 * HSMS 프레임 모델
 *
 * 전송 형식(일반적인 HSMS)
 * - length(4 bytes, big-endian): length = header(10) + body(N)
 * - header(10 bytes)
 * - body(N bytes)
 *
 * 주의
 * - length는 "length 필드 자체(4 bytes)"를 포함하지 않는 것이 보통입니다.
 */
public record HsmsFrame(
        int length,      // header(10) + body(N)
        HsmsHeader header,
        byte[] body
) {
    public HsmsFrame {
        if (length < 10) throw new IllegalArgumentException("length must be >= 10");
        if (header == null) throw new IllegalArgumentException("header is required");
        if (body == null) body = new byte[0];
        if (length != 10 + body.length) {
            throw new IllegalArgumentException("length mismatch: length=" + length + ", bodyLen=" + body.length);
        }
    }

    
    /**
     * HSMS 통신 모듈의 현재 값을 조회합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean isDataMessage() {
        return header.isDataMessage() && header.pType() == HsmsPType.SECS_II;
    }
}
