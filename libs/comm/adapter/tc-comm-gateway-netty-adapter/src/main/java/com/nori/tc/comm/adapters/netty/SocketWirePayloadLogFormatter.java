package com.nori.tc.comm.adapters.netty;

import java.nio.charset.StandardCharsets;

/**
 * SOCKET wire payload 로그 출력을 사람이 읽기 쉬운 형태로 변환하는 유틸리티입니다.
 *
 * <p>게이트웨이/설비 간 송수신 payload는 raw bytes 기반으로 처리되므로, 로그에는 다음 정보를 함께 남겨야
 * 실제 문제 분석(줄바꿈 누락, delimiter mismatch, 인코딩 오인식 등)이 가능합니다.</p>
 * <p>1) 전체 byte 길이(totalBytes)</p>
 * <p>2) UTF-8 기준 텍스트 미리보기(textPreview, 제어문자 escape)</p>
 * <p>3) HEX 미리보기(hexPreview, 앞부분만 잘라서 출력)</p>
 *
 * <p>주의: 로그 폭주를 막기 위해 text/hex preview 길이는 제한하며, 잘린 경우 truncated 표기를 추가합니다.</p>
 */
final class SocketWirePayloadLogFormatter {

    /**
     * 텍스트 미리보기에 사용할 최대 byte 수입니다.
     *
     * <p>SOCKET 프로토콜이 주로 텍스트 기반이므로 명령/응답 식별이 가능한 수준(초반부)만 출력합니다.</p>
     */
    private static final int MAX_TEXT_PREVIEW_BYTES = 256;

    /**
     * HEX 미리보기에 사용할 최대 byte 수입니다.
     *
     * <p>delimiter/개행/비가시 문자를 빠르게 확인할 수 있도록 text preview보다 짧게 유지합니다.</p>
     */
    private static final int MAX_HEX_PREVIEW_BYTES = 64;

    /**
     * HEX 문자열 생성을 위한 대문자 16진수 문자표입니다.
     */
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /**
     * 인스턴스 생성이 필요 없는 static 유틸리티이므로 생성자를 막습니다.
     */
    private SocketWirePayloadLogFormatter() {
    }

    /**
     * raw payload를 로그용 단일 문자열로 요약합니다.
     *
     * <p>반환 문자열 예시:</p>
     * <p>{@code totalBytes=16, textPreview="CMD=INITIALIZE\n", hexPreview="43 4D 44 ..."}</p>
     *
     * @param payload raw payload bytes
     * @return 로그 메시지에 바로 포함할 수 있는 요약 문자열
     */
    static String describe(final byte[] payload) {
        if (payload == null) {
            return "totalBytes=null, textPreview=<null>, hexPreview=<null>";
        }

        final int totalBytes = payload.length;
        final int textPreviewBytes = Math.min(totalBytes, MAX_TEXT_PREVIEW_BYTES);
        final int hexPreviewBytes = Math.min(totalBytes, MAX_HEX_PREVIEW_BYTES);

        final String textPreview = escapeForSingleLineLog(new String(payload, 0, textPreviewBytes, StandardCharsets.UTF_8));
        final boolean textTruncated = totalBytes > textPreviewBytes;
        final String hexPreview = buildHexPreview(payload, hexPreviewBytes, totalBytes > hexPreviewBytes);

        return "totalBytes=" + totalBytes
                + ", textPreview=\"" + textPreview + (textTruncated ? "...(truncated)" : "") + "\""
                + ", hexPreview=\"" + hexPreview + "\"";
    }

    /**
     * payload 앞부분을 공백 구분 HEX 문자열로 변환합니다.
     *
     * @param payload source bytes
     * @param length preview 길이(byte)
     * @param truncated 실제 payload가 preview보다 긴지 여부
     * @return 로그용 HEX preview 문자열
     */
    private static String buildHexPreview(final byte[] payload, final int length, final boolean truncated) {
        if (length <= 0) {
            return "";
        }

        final StringBuilder sb = new StringBuilder(length * 3 + 24);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            final int v = payload[i] & 0xFF;
            sb.append(HEX[v >>> 4]).append(HEX[v & 0x0F]);
        }
        if (truncated) {
            sb.append(" ...(truncated)");
        }
        return sb.toString();
    }

    /**
     * 단일 라인 로그를 깨뜨릴 수 있는 문자들을 escape 처리합니다.
     *
     * <p>개행/탭은 사람이 식별 가능한 escape 시퀀스로 치환하고, 기타 ISO 제어문자는 `\xNN` 형태로 표시합니다.</p>
     *
     * @param text UTF-8 기준 텍스트 미리보기 문자열
     * @return 단일 라인 로그에 안전한 문자열
     */
    private static String escapeForSingleLineLog(final String text) {
        if (text == null) {
            return "null";
        }

        final StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\r' -> escaped.append("\\r");
                case '\n' -> escaped.append("\\n");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isISOControl(ch)) {
                        escaped.append("\\x");
                        final int v = ch & 0xFF;
                        escaped.append(HEX[v >>> 4]).append(HEX[v & 0x0F]);
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
