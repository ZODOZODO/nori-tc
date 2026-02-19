package com.nori.tc.comm.gateway.hsms.frame;

/**
 * Big-endian(네트워크 바이트 오더) 유틸
 *
 * 목적
 * - HSMS 길이(4 bytes), 헤더 필드(deviceId/systemBytes) 파싱을 간결하고 오류 없이 수행하기 위함입니다.
 */
public final class ByteOrderUtil {

    
    /**
     * ByteOrderUtil 생성자를 초기화합니다.
     *
     */

    private ByteOrderUtil() {}

    
    /**
     * HSMS 통신 모듈에서 필요한 데이터를 조회합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param b HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param offset 페이징/조회 범위 조건
     * @return HSMS 통신 모듈 처리 결과
     */
    public static int readInt32BE(final byte[] b, final int offset) {
        return ((b[offset] & 0xFF) << 24)
                | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8)
                | (b[offset + 3] & 0xFF);
    }

    
    /**
     * HSMS 통신 모듈에서 필요한 데이터를 조회합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param b HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param offset 페이징/조회 범위 조건
     * @return HSMS 통신 모듈 처리 결과
     */
    public static int readUInt16BE(final byte[] b, final int offset) {
        return ((b[offset] & 0xFF) << 8) | (b[offset + 1] & 0xFF);
    }

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param b HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param offset 페이징/조회 범위 조건
     * @param value HSMS 통신 모듈 처리에 사용하는 입력 값
     */
    public static void writeInt32BE(final byte[] b, final int offset, final int value) {
        // 출력 단계: 결과를 외부 저장소/브로커로 반영합니다.
        b[offset] = (byte) (value >>> 24);
        b[offset + 1] = (byte) (value >>> 16);
        b[offset + 2] = (byte) (value >>> 8);
        b[offset + 3] = (byte) (value);
    }

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param b HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param offset 페이징/조회 범위 조건
     * @param value HSMS 통신 모듈 처리에 사용하는 입력 값
     */
    public static void writeUInt16BE(final byte[] b, final int offset, final int value) {
        b[offset] = (byte) (value >>> 8);
        b[offset + 1] = (byte) (value);
    }
}
