package com.nori.tc.comm.gateway.socket.socketType.types.regexDelimited;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.gateway.socket.frame.SocketFrame;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;

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

    
    /**
     * 소켓 통신 모듈 구성 요소를 초기화합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @param endRegex 소켓 통신 모듈 처리에 사용하는 입력 값
     */
    public RegexDelimitedSocketTypeHandler(final String endRegex) {
        this(endRegex, StandardCharsets.UTF_8);
    }

    
    /**
     * 소켓 통신 모듈 구성 요소를 초기화합니다.
     *
     * <p>소켓 타입 분기, 인코딩/디코딩, 연결 상태 관리를 기준으로 동작합니다.</p>
     * @param endRegex 소켓 통신 모듈 처리에 사용하는 입력 값
     * @param charset 소켓 통신 모듈 처리에 사용하는 입력 값
     */
    public RegexDelimitedSocketTypeHandler(final String endRegex, final Charset charset) {
        if (endRegex == null || endRegex.isBlank()) {
            throw new IllegalArgumentException("endRegex is required");
        }
        this.endPattern = Pattern.compile(endRegex);
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
        if (frameBytes == null) {
            throw new IllegalArgumentException("frameBytes is null");
        }

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

    /**
     * outbound 명령 문자열을 wire bytes로 인코딩합니다.
     *
     * <p>현재 정책:
     * - rawMessage 원문을 그대로 bytes로 변환합니다.
     * - 정규식 종단 패턴 보정/추가는 수행하지 않습니다.</p>
     *
     * @param command rawMessage 문자열(또는 문자열 변환 가능한 객체)
     * @return socket 송신용 bytes 결과
     */
    @Override
    public SocketTypeEncodeResult encode(final Object command) {
        if (command == null) {
            throw new IllegalArgumentException("command is null");
        }

        final String rawMessage = String.valueOf(command);
        final byte[] encoded = rawMessage.getBytes(charset);
        return new SocketTypeEncodeResult(encoded, "regex-delimited pass-through encoding");
    }
}
