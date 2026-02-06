package com.nori.tc.comm.socket.socketType.builtin.regexDelimited;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.socket.frame.SocketFrame;
import com.nori.tc.comm.socket.socketType.SocketTypeDecodeResult;
import com.nori.tc.comm.socket.socketType.SocketTypeHandler;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-delimited socketType 핸들러(기본 제공)
 *
 * 목적
 * - 특정 종료 패턴(예: "END\n", "<ETX>" 등)을 정규식으로 찾아 프레임을 추출합니다.
 *
 * 사용 예
 * - endPattern = "END\\n"
 *
 * 주의
 * - 정규식 탐색은 비용이 큽니다.
 * - 고성능/대량 트래픽에서는 line-delimited, length-delimited 같은 방식이 더 유리합니다.
 */
public final class RegexDelimitedSocketTypeHandler implements SocketTypeHandler {

    public static final String SOCKET_TYPE = "REGEX_DELIMITED";

    private final Charset charset;
    private final Pattern endPattern;

    public RegexDelimitedSocketTypeHandler(final String endRegex) {
        this(endRegex, StandardCharsets.UTF_8);
    }

    public RegexDelimitedSocketTypeHandler(final String endRegex, final Charset charset) {
        if (endRegex == null || endRegex.isBlank()) {
            throw new IllegalArgumentException("endRegex is required");
        }
        this.endPattern = Pattern.compile(endRegex);
        this.charset = (charset == null) ? StandardCharsets.UTF_8 : charset;
    }

    @Override
    public String socketType() {
        return SOCKET_TYPE;
    }

    @Override
    public SocketFrame tryExtractOne(final ReassemblyBuffer buffer, final int maxFrameBytes) {
        final int readable = buffer.readableBytes();
        if (readable == 0) return null;

        // buffer 전체를 문자열로 변환(주의: 큰 데이터면 비용 큼)
        // - 운영에서는 "최대 탐색 길이" 제한을 추가하거나, 더 효율적인 방식으로 최적화할 수 있습니다.
        final byte[] snapshot = buffer.copy(0, readable);
        final String s = new String(snapshot, charset);

        final Matcher m = endPattern.matcher(s);
        if (!m.find()) {
            return null;
        }

        final int endIndexExclusive = m.end(); // 매칭 끝(문자 기준)
        // 문자 인덱스를 바이트 인덱스로 환산해야 하지만,
        // UTF-8 등 가변길이 인코딩에서는 정확히 매핑이 어렵습니다.
        // 따라서 이 핸들러는 “ASCII 기반 프로토콜”에서만 사용을 권장합니다.
        //
        // 안전하게: 문자열의 앞부분을 다시 bytes로 인코딩하여 바이트 길이를 계산합니다.
        final byte[] frameBytes = s.substring(0, endIndexExclusive).getBytes(charset);

        if (frameBytes.length > maxFrameBytes) {
            throw new IllegalStateException("Socket frame too large: " + frameBytes.length + " > " + maxFrameBytes);
        }

        buffer.discard(frameBytes.length);

        return new SocketFrame(frameBytes, System.currentTimeMillis());
    }

    @Override
    public SocketTypeDecodeResult decode(final byte[] frameBytes) {
        final String text = new String(frameBytes, charset).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Empty regex frame");
        }

        // 기본 decode: 첫 토큰을 messageName으로 사용
        final String[] parts = text.split("\\s+", 2);
        final String messageName = parts[0];
        final String bodyText = (parts.length > 1) ? parts[1] : "";

        return new SocketTypeDecodeResult(
                messageName,
                Map.of("rawText", text),
                bodyText
        );
    }
}
