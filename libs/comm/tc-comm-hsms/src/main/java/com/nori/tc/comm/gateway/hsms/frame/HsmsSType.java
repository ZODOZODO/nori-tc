package com.nori.tc.comm.gateway.hsms.frame;

/**
 * HSMS SType (Session Type)
 *
 * 일반적으로 많이 쓰는 값만 정의합니다.
 * - DATA(0): SECS-II Data Message
 * - SELECT / DESELECT / LINKTEST 등 제어 메시지
 */
public enum HsmsSType {
    
    DATA(0),
    SELECT_REQ(1),
    SELECT_RSP(2),
    DESELECT_REQ(3),
    DESELECT_RSP(4),
    LINKTEST_REQ(5),
    LINKTEST_RSP(6),
    REJECT_REQ(7),
    SEPARATE_REQ(9);

    private final int code;

    
    /**
     * HSMS 통신 모듈 구성 요소를 초기화합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param code HSMS 통신 모듈 처리에 사용하는 입력 값
     */
    HsmsSType(final int code) {
        this.code = code;
    }

    
    /**
     * HSMS 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @return HSMS 통신 모듈 처리 결과
     */
    public int code() {
        return code;
    }

    
    /**
     * HSMS 통신 모듈 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>SEMI HSMS 규격의 세션/메시지 처리 절차를 기준으로 동작합니다.</p>
     * @param code HSMS 통신 모듈 처리에 사용하는 입력 값
     * @return HSMS 통신 모듈 처리 결과
     */
    public static HsmsSType fromCode(final int code) {
        for (HsmsSType t : values()) {
            if (t.code == code) return t;
        }
        // 알 수 없는 SType은 운영상 중요한 이벤트이므로 즉시 실패시키는 것이 보통 안전합니다.
        throw new IllegalArgumentException("Unknown HSMS SType code: " + code);
    }
}
