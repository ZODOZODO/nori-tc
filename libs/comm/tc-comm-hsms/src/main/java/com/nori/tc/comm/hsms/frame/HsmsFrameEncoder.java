package com.nori.tc.comm.hsms.frame;

/**
 * HSMS 프레임 인코더
 *
 * 목적
 * - session state machine이 생성한 control frame(SELECT_RSP, LINKTEST_RSP 등)을
 *   raw bytes로 만들어 OutboundRawFrame으로 송신하기 위함입니다.
 */
public final class HsmsFrameEncoder {

    private HsmsFrameEncoder() {}

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
