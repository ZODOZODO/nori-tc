package com.nori.tc.comm.hsms.frame;

/**
 * HSMS 10바이트 헤더 모델
 *
 * 구성(일반적인 HSMS/SECS 헤더)
 * - deviceId(2) + stream(1) + function(1) + pType(1) + sType(1) + systemBytes(4)
 *
 * stream 바이트의 최상위 비트는 W-bit(Reply expected)로 쓰는 경우가 많습니다.
 * - streamByte = (wBit ? 0x80 : 0) | (stream & 0x7F)
 */
public record HsmsHeader(
        int deviceId,       // 0..65535
        int stream,         // 0..127 (W-bit 제외)
        int function,       // 0..255
        boolean wBit,       // reply expected
        int pType,          // usually 0x00
        HsmsSType sType,    // DATA/CONTROL
        int systemBytes     // 32-bit correlation id (unsigned 취급)
) {
    public HsmsHeader {
        if (deviceId < 0 || deviceId > 0xFFFF) throw new IllegalArgumentException("deviceId must be 0..65535");
        if (stream < 0 || stream > 0x7F) throw new IllegalArgumentException("stream must be 0..127");
        if (function < 0 || function > 0xFF) throw new IllegalArgumentException("function must be 0..255");
        if (sType == null) throw new IllegalArgumentException("sType is required");
        if (pType < 0 || pType > 0xFF) throw new IllegalArgumentException("pType must be 0..255");
    }

    /**
     * HSMS 데이터 메시지인지 여부
     */
    public boolean isDataMessage() {
        return sType == HsmsSType.DATA;
    }

    /**
     * 10바이트 헤더로 직렬화합니다.
     */
    public byte[] toBytes() {
        final byte[] h = new byte[10];

        ByteOrderUtil.writeUInt16BE(h, 0, deviceId);

        final int streamByte = (wBit ? 0x80 : 0) | (stream & 0x7F);
        h[2] = (byte) streamByte;
        h[3] = (byte) (function & 0xFF);
        h[4] = (byte) (pType & 0xFF);
        h[5] = (byte) (sType.code() & 0xFF);

        ByteOrderUtil.writeInt32BE(h, 6, systemBytes);

        return h;
    }

    /**
     * 10바이트 헤더를 파싱합니다.
     */
    public static HsmsHeader parse(final byte[] header10) {
        if (header10 == null) throw new IllegalArgumentException("header10 is null");
        if (header10.length != 10) throw new IllegalArgumentException("header10 length must be 10");

        final int deviceId = ByteOrderUtil.readUInt16BE(header10, 0);

        final int streamByte = header10[2] & 0xFF;
        final boolean wBit = (streamByte & 0x80) != 0;
        final int stream = streamByte & 0x7F;

        final int function = header10[3] & 0xFF;
        final int pType = header10[4] & 0xFF;
        final int sTypeCode = header10[5] & 0xFF;

        final int systemBytes = ByteOrderUtil.readInt32BE(header10, 6);

        return new HsmsHeader(
                deviceId,
                stream,
                function,
                wBit,
                pType,
                HsmsSType.fromCode(sTypeCode),
                systemBytes
        );
    }
}
