package com.nori.tc.comm.socket.socketType.builtin.lineDelimited;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.socket.frame.SocketFrame;
import com.nori.tc.comm.socket.socketType.SocketTypeDecodeResult;
import com.nori.tc.comm.socket.socketType.SocketTypeHandler;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Line-delimited socketType 핸들러(기본 제공)
 *
 * 프레이밍 규칙
 * - '\n' (LF)를 프레임 종료로 사용합니다.
 * - '\r\n'도 들어올 수 있으므로, decode 시 '\r' 제거 처리를 합니다.
 *
 * decode 규칙(기본)
 * - 한 줄의 첫 토큰을 messageName으로 사용하고, 나머지는 bodyRawText로 둡니다.
 *
 * 예)
 * - "EVT PORT_STATUS 1 2 3\n" -> messageName="EVT", body="PORT_STATUS 1 2 3"
 *
 * 주의
 * - 이것은 “샘플/기본 핸들러”입니다.
 * - 실제 설비별 프로토콜은 더 복잡할 수 있으므로 socketType별로 전용 핸들러를 두는 것을 권장합니다.
 */
public final class LineDelimitedSocketTypeHandler implements SocketTypeHandler {

    public static final String SOCKET_TYPE = "LINE_DELIMITED";

    private final Charset charset;

    public LineDelimitedSocketTypeHandler() {
        this(StandardCharsets.UTF_8);
    }

    public LineDelimitedSocketTypeHandler(final Charset charset) {
        this.charset = (charset == null) ? StandardCharsets.UTF_8 : charset;
    }

    @Override
    public String socketType() {
        return SOCKET_TYPE;
    }

    @Override
    public SocketFrame tryExtractOne(final ReassemblyBuffer buffer, final int maxFrameBytes) {
        // '\n' 탐색
        final int readable = buffer.readableBytes();
        if (readable == 0) return null;

        int lfIndex = -1;
        for (int i = 0; i < readable; i++) {
            if (buffer.get(i) == (byte) '\n') {
                lfIndex = i;
                break;
            }
        }
        if (lfIndex < 0) return null;

        // 프레임 길이 = lfIndex 포함(종료문자까지)
        final int frameLen = lfIndex + 1;
        if (frameLen > maxFrameBytes) {
            throw new IllegalStateException("Socket frame too large: " + frameLen + " > " + maxFrameBytes);
        }

        final byte[] frame = buffer.copy(0, frameLen);
        buffer.discard(frameLen);

        return new SocketFrame(frame, System.currentTimeMillis());
    }

    @Override
    public SocketTypeDecodeResult decode(final byte[] frameBytes) {
        if (frameBytes == null) throw new IllegalArgumentException("frameBytes is null");

        // 문자열로 변환 후 개행 제거
        String line = new String(frameBytes, charset);
        line = line.replace("\r", "").replace("\n", "").trim();

        if (line.isEmpty()) {
            // 빈 프레임을 허용할지 여부는 상위에서 결정(일단 여기서는 실패)
            throw new IllegalArgumentException("Empty line frame");
        }

        // 첫 토큰을 messageName으로 사용
        final String[] parts = line.split("\\s+", 2);
        final String messageName = parts[0];
        final String bodyText = (parts.length > 1) ? parts[1] : "";

        return new SocketTypeDecodeResult(
                messageName,
                Map.of("rawLine", line),
                bodyText
        );
    }
}
