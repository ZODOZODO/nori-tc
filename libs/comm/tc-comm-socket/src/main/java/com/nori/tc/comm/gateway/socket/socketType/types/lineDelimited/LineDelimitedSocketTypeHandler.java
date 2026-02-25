package com.nori.tc.comm.gateway.socket.socketType.types.lineDelimited;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.gateway.socket.frame.SocketFrame;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;

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

    
    /**
     * 소켓 통신 모듈 구성 요소를 초기화합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     */
    public LineDelimitedSocketTypeHandler() {
        this(StandardCharsets.UTF_8);
    }

    
    /**
     * 소켓 통신 모듈 구성 요소를 초기화합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @param charset 소켓 통신 모듈 처리에 사용하는 입력 값
     */
    public LineDelimitedSocketTypeHandler(final Charset charset) {
        this.charset = (charset == null) ? StandardCharsets.UTF_8 : charset;
    }

    
    /**
     * 소켓 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @return 소켓 통신 모듈 처리 결과
     */
    @Override
    public String socketType() {
        return SOCKET_TYPE;
    }

    
    /**
     * 소켓 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @param buffer 소켓 통신 모듈 처리에 사용하는 입력 값
     * @param maxFrameBytes 처리할 원본 데이터
     * @return 소켓 통신 모듈 처리 결과
     */
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

    
    /**
     * 소켓 통신 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @param frameBytes 처리할 원본 데이터
     * @return 소켓 통신 모듈 처리 결과
     */
    @Override
    public SocketTypeDecodeResult decode(final byte[] frameBytes) {
        // 파싱 단계: 입력 포맷을 해석해 필요한 필드만 안전하게 추출합니다.
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

    /**
     * outbound 명령 문자열을 wire bytes로 인코딩합니다.
     *
     * <p>현재 정책:
     * - rawMessage 원문을 그대로 bytes로 변환합니다.
     * - 개행/종단문자 보정은 수행하지 않습니다.</p>
     *
     * @param command rawMessage 문자열(또는 문자열 변환 가능한 객체)
     * @return socket 송신용 bytes 결과
     */
    @Override
    public SocketTypeEncodeResult encode(final Object command) {
        if (command == null) {
            throw new IllegalArgumentException("command is null");
        }

        /*
         * LINE_DELIMITED 프로토콜의 송신 프레임은 LF('\n')로 종료되어야만 상대 설비가 한 줄(frame)로 인식할 수 있습니다.
         *
         * 기존 구현은 문자열을 그대로 bytes로만 변환(pass-through)해서,
         * 설정값에 LF가 빠지면 "CMD=INITIALIZE"만 전송되고 설비가 프레임 종료를 인식하지 못하는 문제가 발생했습니다.
         *
         * 따라서 LINE_DELIMITED encode 단계에서 마지막 LF를 자동 보정합니다.
         * - 이미 "\n" 또는 "\r\n"로 끝나는 경우: 그대로 유지 (중복 LF 방지)
         * - LF가 없는 경우: "\n" 추가
         */
        final String rawMessage = String.valueOf(command);
        final String normalizedMessage = rawMessage.endsWith("\n")
                ? rawMessage
                : rawMessage + "\n";

        final byte[] encoded = normalizedMessage.getBytes(charset);
        return new SocketTypeEncodeResult(encoded,
                normalizedMessage.equals(rawMessage)
                        ? "line-delimited encoding (LF already present)"
                        : "line-delimited encoding (LF appended)");
    }
}
