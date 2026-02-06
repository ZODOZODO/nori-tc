package com.nori.tc.comm.hsms.frame;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;

/**
 * ReassemblyBuffer에서 HSMS 프레임을 "가능한 만큼" 추출하는 컴포넌트
 *
 * 책임
 * - length(4) 기반 프레이밍
 * - 헤더(10) 파싱
 * - 바이트 소비(discard)
 *
 * 오류 처리
 * - 프레임 길이가 비정상(too small/too large)하면 IllegalArgumentException/IllegalStateException으로 실패
 *   (상위(core)가 DLQ/Quarantine로 처리하도록)
 */
public final class HsmsFrameExtractor {

    private final int maxFrameBytes;

    /**
     * @param maxFrameBytes 프레임 최대 바이트(길이 필드 제외한 length 상한)
     */
    public HsmsFrameExtractor(final int maxFrameBytes) {
        if (maxFrameBytes <= 0) throw new IllegalArgumentException("maxFrameBytes must be > 0");
        this.maxFrameBytes = maxFrameBytes;
    }

    /**
     * 버퍼에서 프레임 1개를 추출합니다.
     *
     * @return 추출 성공 시 HsmsFrame, 아직 데이터가 부족하면 null
     */
    public HsmsFrame tryExtractOne(final ReassemblyBuffer buffer) {
        if (buffer.readableBytes() < 4) {
            return null; // length를 읽을 수 없음
        }

        // length(4 bytes, big-endian)
        final byte[] lenBytes = buffer.copy(0, 4);
        final int length = ByteOrderUtil.readInt32BE(lenBytes, 0);

        if (length < 10) {
            throw new IllegalArgumentException("HSMS length too small: " + length);
        }
        if (length > maxFrameBytes) {
            throw new IllegalStateException("HSMS length too large: " + length + " > " + maxFrameBytes);
        }

        final int totalNeeded = 4 + length;
        if (buffer.readableBytes() < totalNeeded) {
            return null; // 아직 프레임 전체가 안 들어옴
        }

        // header(10)
        final byte[] header10 = buffer.copy(4, 10);
        final HsmsHeader header = HsmsHeader.parse(header10);

        // body(N)
        final int bodyLen = length - 10;
        final byte[] body = (bodyLen > 0) ? buffer.copy(4 + 10, bodyLen) : new byte[0];

        // consume
        buffer.discard(totalNeeded);

        return new HsmsFrame(length, header, body);
    }
}
