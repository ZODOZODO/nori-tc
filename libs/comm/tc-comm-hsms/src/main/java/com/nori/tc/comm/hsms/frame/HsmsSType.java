package com.nori.tc.comm.hsms.frame;

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

    HsmsSType(final int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static HsmsSType fromCode(final int code) {
        for (HsmsSType t : values()) {
            if (t.code == code) return t;
        }
        // 알 수 없는 SType은 운영상 중요한 이벤트이므로 즉시 실패시키는 것이 보통 안전합니다.
        throw new IllegalArgumentException("Unknown HSMS SType code: " + code);
    }
}
