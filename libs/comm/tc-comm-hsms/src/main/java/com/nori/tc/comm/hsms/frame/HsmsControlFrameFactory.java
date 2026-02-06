package com.nori.tc.comm.hsms.frame;

/**
 * HSMS Control 프레임 생성 유틸
 *
 * 원칙
 * - Control message는 보통 body가 없습니다.
 * - correlation을 위해 systemBytes는 요청 프레임의 systemBytes를 그대로 사용합니다.
 *
 * 이 유틸은 “표준적인 형태”의 프레임만 만듭니다.
 * 특이한 벤더 요구사항이 있으면 앱 레이어 또는 별도 확장 포인트에서 처리하세요.
 */
public final class HsmsControlFrameFactory {

    private HsmsControlFrameFactory() {}

    public static HsmsFrame selectRsp(final int deviceId, final int systemBytes) {
        final HsmsHeader header = new HsmsHeader(
                deviceId,
                0, 0,
                false,
                HsmsPType.SECS_II,
                HsmsSType.SELECT_RSP,
                systemBytes
        );
        return new HsmsFrame(10, header, new byte[0]);
    }

    public static HsmsFrame deselectRsp(final int deviceId, final int systemBytes) {
        final HsmsHeader header = new HsmsHeader(
                deviceId,
                0, 0,
                false,
                HsmsPType.SECS_II,
                HsmsSType.DESELECT_RSP,
                systemBytes
        );
        return new HsmsFrame(10, header, new byte[0]);
    }

    public static HsmsFrame linktestRsp(final int deviceId, final int systemBytes) {
        final HsmsHeader header = new HsmsHeader(
                deviceId,
                0, 0,
                false,
                HsmsPType.SECS_II,
                HsmsSType.LINKTEST_RSP,
                systemBytes
        );
        return new HsmsFrame(10, header, new byte[0]);
    }

    public static HsmsFrame linktestReq(final int deviceId, final int systemBytes) {
        final HsmsHeader header = new HsmsHeader(
                deviceId,
                0, 0,
                false,
                HsmsPType.SECS_II,
                HsmsSType.LINKTEST_REQ,
                systemBytes
        );
        return new HsmsFrame(10, header, new byte[0]);
    }

    public static HsmsFrame selectReq(final int deviceId, final int systemBytes) {
        final HsmsHeader header = new HsmsHeader(
                deviceId,
                0, 0,
                false,
                HsmsPType.SECS_II,
                HsmsSType.SELECT_REQ,
                systemBytes
        );
        return new HsmsFrame(10, header, new byte[0]);
    }
}
