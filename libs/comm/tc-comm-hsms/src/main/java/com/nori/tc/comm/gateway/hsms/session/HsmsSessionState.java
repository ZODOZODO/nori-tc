package com.nori.tc.comm.gateway.hsms.session;

/**
 * HSMS 세션 상태(단순화 버전)
 *
 * 현실
 * - HSMS의 전체 상태 머신을 완전 구현하려면 케이스가 많아집니다.
 * - 본 프로젝트에서는 "무유실/순차 처리/저지연" 관점에서,
 *   실무에서 핵심이 되는 Selected 여부 중심으로 단순화하여 뼈대를 제공합니다.
 */
public enum HsmsSessionState {
    NOT_SELECTED,
    SELECTED
}
