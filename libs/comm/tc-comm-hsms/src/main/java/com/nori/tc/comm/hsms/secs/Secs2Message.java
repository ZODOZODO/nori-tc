package com.nori.tc.comm.hsms.secs;

/**
 * SECS-II 디코딩 결과(최소 모델)
 *
 * 현실적인 운영 포인트
 * - 완전한 SECS-II 타입 시스템(L, A, U1, I2 ...)을 구현하면 파일/코드량이 급격히 커집니다.
 * - 본 프로젝트의 핵심은 “게이트웨이 파이프라인/무유실/순차성/동적 정책”이므로,
 *   우선은 최소 단위(SxFy + raw body)로 뼈대를 제공합니다.
 *
 * 추후 확장
 * - 설비별 모델(tc_model_secs_message) 기반으로 실제 타입 파싱을 추가하거나,
 * - 외부 검증된 SECS-II 라이브러리를 adapter로 붙이는 방식을 권장합니다.
 */
public record Secs2Message(
        int stream,
        int function,
        boolean wBit,
        byte[] rawBody
) {
    public Secs2Message {
        if (stream < 0 || stream > 127) throw new IllegalArgumentException("stream must be 0..127");
        if (function < 0 || function > 255) throw new IllegalArgumentException("function must be 0..255");
        if (rawBody == null) rawBody = new byte[0];
    }

    /**
     * 표준 메시지명 "S{stream}F{function}" 형태
     */
    public String messageName() {
        return "S" + stream + "F" + function;
    }
}
