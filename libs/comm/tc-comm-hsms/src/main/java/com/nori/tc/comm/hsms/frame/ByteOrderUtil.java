package com.nori.tc.comm.hsms.frame;

/**
 * Big-endian(네트워크 바이트 오더) 유틸
 *
 * 목적
 * - HSMS 길이(4 bytes), 헤더 필드(deviceId/systemBytes) 파싱을 간결하고 오류 없이 수행하기 위함입니다.
 */
public final class ByteOrderUtil {

    private ByteOrderUtil() {}

    public static int readInt32BE(final byte[] b, final int offset) {
        return ((b[offset] & 0xFF) << 24)
                | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8)
                | (b[offset + 3] & 0xFF);
    }

    public static int readUInt16BE(final byte[] b, final int offset) {
        return ((b[offset] & 0xFF) << 8) | (b[offset + 1] & 0xFF);
    }

    public static void writeInt32BE(final byte[] b, final int offset, final int value) {
        b[offset] = (byte) (value >>> 24);
        b[offset + 1] = (byte) (value >>> 16);
        b[offset + 2] = (byte) (value >>> 8);
        b[offset + 3] = (byte) (value);
    }

    public static void writeUInt16BE(final byte[] b, final int offset, final int value) {
        b[offset] = (byte) (value >>> 8);
        b[offset + 1] = (byte) (value);
    }
}
