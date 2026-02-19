package com.nori.tc.comm.gateway.hsms.frame;

/**
 * HSMS 프레임 인코더
 *
 * 목적
 * - session state machine이 생성한 control frame(SELECT_RSP, LINKTEST_RSP 등)을
 *   raw bytes로 만들어 OutboundRawFrame으로 송신하기 위함입니다.
 */
public final class HsmsFrameEncoder {

    
    /**
     * HsmsFrameEncoder 생성자를 초기화합니다.
     *
     */

    private HsmsFrameEncoder() {}

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param frame HSMS 통신 모듈 처리에 사용하는 입력 값
     * @return HSMS 통신 모듈 처리 결과
     */
    public static byte[] encode(final HsmsFrame frame) {
        if (frame == null) throw new IllegalArgumentException("frame is null");

        final int length = frame.length();
        final byte[] out = new byte[4 + length];

        // length(4) + header(10) + body(N)
        ByteOrderUtil.writeInt32BE(out, 0, length);

        final byte[] header10 = frame.header().toBytes();
        System.arraycopy(header10, 0, out, 4, 10);

        if (frame.body().length > 0) {
            System.arraycopy(frame.body(), 0, out, 4 + 10, frame.body().length);
        }

        return out;
    }
}
