package com.nori.tc.comm.gateway.hsms.frame;

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

    
    /**
     * HsmsControlFrameFactory 생성자를 초기화합니다.
     *
     */

    private HsmsControlFrameFactory() {}

    
    /**
     * HSMS 통신 모듈에서 필요한 데이터를 조회합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param deviceId HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param systemBytes 처리할 원본 데이터
     * @return HSMS 통신 모듈 처리 결과
     */
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

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param deviceId HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param systemBytes 처리할 원본 데이터
     * @return HSMS 통신 모듈 처리 결과
     */
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

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param deviceId HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param systemBytes 처리할 원본 데이터
     * @return HSMS 통신 모듈 처리 결과
     */
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

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param deviceId HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param systemBytes 처리할 원본 데이터
     * @return HSMS 통신 모듈 처리 결과
     */
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

    
    /**
     * HSMS 통신 모듈에서 필요한 데이터를 조회합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param deviceId HSMS 통신 모듈 처리에 사용하는 입력 값
     * @param systemBytes 처리할 원본 데이터
     * @return HSMS 통신 모듈 처리 결과
     */
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
