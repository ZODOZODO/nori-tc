package com.nori.tc.comm.hsms.frame;

/**
 * HSMS PType (Presentation Type)
 *
 * 통상
 * - 0x00: SECS-II (Data Message)
 *
 * 주의
 * - 실제 현장에서는 대부분 0만 사용합니다.
 * - 본 모듈은 확장 가능성을 위해 상수로 분리합니다.
 */
public final class HsmsPType {
    private HsmsPType() {}

    public static final int SECS_II = 0x00;
}
