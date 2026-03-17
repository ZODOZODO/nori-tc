package com.nori.tc.comm.gateway.socket.socketType.types.regexDelimited;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.gateway.action.SocketFrame;
import com.nori.tc.comm.gateway.action.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.action.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.action.SocketTypeHandler;

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
    private static final String REGEX_META_CHARS = ".^$|?*+()[]{}";

    private final Charset charset;
    private final Pattern endPattern;
    /**
     * endRegex가 "단순 literal + escape(\\n, \\r, \\t, \\\\)" 형태일 때만 encode 단계에서 자동 부착할 종단자입니다.
     *
     * <p>REGEX_DELIMITED의 종료 조건은 정규식이므로 일반적으로는 역변환(정규식 -> 실제 suffix)이 불가능합니다.
     * 예를 들어 {@code \r?\n}, {@code (END|EOM)\n} 같은 패턴은 어떤 문자열을 붙여야 하는지 단정할 수 없습니다.</p>
     *
     * <p>따라서 본 필드는 "안전하게 literal suffix로 해석 가능한 경우"에만 값이 채워지며,
     * 그 외 복잡한 정규식 패턴에서는 {@code null}로 유지되어 encode가 pass-through로 동작합니다.</p>
     */
    private final String autoAppendSuffix;

    
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
        this.autoAppendSuffix = tryResolveLiteralSuffixFromRegex(endRegex);
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
        final boolean alreadyTerminated = endsWithRegexDelimiter(rawMessage);

        /*
         * REGEX_DELIMITED는 기본적으로 decode 기준 종료 패턴(endRegex)을 사용해 프레임을 자릅니다.
         * 따라서 encode에서도 같은 종료 패턴이 wire에 존재해야 설비가 프레임 종료를 인식할 수 있습니다.
         *
         * 다만 endRegex는 "정규식"이라서 항상 suffix를 역으로 만들 수는 없으므로:
         * 1) 이미 문자열 끝에서 종료 패턴이 매칭되면 그대로 전송
         * 2) literal suffix로 안전하게 해석 가능한 패턴이면 자동 부착
         * 3) 복잡한 패턴이면 기존 pass-through 유지 (설정값에 종단자를 직접 포함해야 함)
         */
        final String normalizedMessage;
        final String description;
        if (alreadyTerminated) {
            normalizedMessage = rawMessage;
            description = "regex-delimited encoding (delimiter already present)";
        } else if (autoAppendSuffix != null) {
            normalizedMessage = rawMessage + autoAppendSuffix;
            description = "regex-delimited encoding (literal suffix appended from endRegex)";
        } else {
            normalizedMessage = rawMessage;
            description = "regex-delimited pass-through encoding (complex endRegex; append manually in command)";
        }

        final byte[] encoded = normalizedMessage.getBytes(charset);
        return new SocketTypeEncodeResult(encoded, description);
    }

    /**
     * 문자열 끝에서 종료 정규식이 매칭되는지 확인합니다.
     *
     * <p>{@link Matcher#find()}를 사용해 모든 매칭을 탐색한 뒤, 마지막 인덱스가 문자열 끝에 닿는 매칭이 하나라도 있으면
     * "이미 종단자 포함"으로 판단합니다.</p>
     *
     * @param message 검사할 outbound 메시지 문자열
     * @return 문자열 끝에서 종료 패턴이 매칭되면 true
     */
    private boolean endsWithRegexDelimiter(final String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        final Matcher matcher = endPattern.matcher(message);
        while (matcher.find()) {
            if (matcher.end() == message.length()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 단순한 정규식 패턴만 literal suffix 문자열로 복원합니다.
     *
     * <p>지원하는 escape:</p>
     * <p>- {@code \\n}, {@code \\r}, {@code \\t}, {@code \\\\}</p>
     * <p>- {@code \\xHH}, {@code \\uHHHH}</p>
     *
     * <p>정규식 메타문자(예: {@code ?}, {@code *}, {@code []}, {@code ()})가 포함되면
     * encode 자동 보정이 모호해지므로 {@code null}을 반환합니다.</p>
     *
     * @param endRegex 생성자에 주입된 종료 정규식 문자열
     * @return 안전하게 해석 가능한 literal suffix, 불가능하면 null
     */
    private String tryResolveLiteralSuffixFromRegex(final String endRegex) {
        if (endRegex == null || endRegex.isBlank()) {
            return null;
        }

        final StringBuilder literal = new StringBuilder(endRegex.length());
        for (int i = 0; i < endRegex.length(); i++) {
            final char ch = endRegex.charAt(i);

            if (ch == '\\') {
                if (i + 1 >= endRegex.length()) {
                    return null;
                }

                final char next = endRegex.charAt(++i);
                switch (next) {
                    case 'n' -> literal.append('\n');
                    case 'r' -> literal.append('\r');
                    case 't' -> literal.append('\t');
                    case '\\' -> literal.append('\\');
                    case 'x' -> {
                        if (i + 2 >= endRegex.length()) {
                            return null;
                        }
                        final String hex = endRegex.substring(i + 1, i + 3);
                        if (!isHex(hex)) {
                            return null;
                        }
                        literal.append((char) Integer.parseInt(hex, 16));
                        i += 2;
                    }
                    case 'u' -> {
                        if (i + 4 >= endRegex.length()) {
                            return null;
                        }
                        final String hex = endRegex.substring(i + 1, i + 5);
                        if (!isHex(hex)) {
                            return null;
                        }
                        literal.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> {
                        /*
                         * 예: \d, \s, \w, \Q...\E 등은 "문자 클래스/특수 escape"일 가능성이 높아서
                         * literal suffix로 안전하게 확정할 수 없습니다.
                         */
                        return null;
                    }
                }
                continue;
            }

            if (REGEX_META_CHARS.indexOf(ch) >= 0) {
                return null;
            }

            literal.append(ch);
        }

        return literal.isEmpty() ? null : literal.toString();
    }

    /**
     * 문자열이 16진수 문자만으로 구성되어 있는지 확인합니다.
     *
     * @param text 검사 대상 문자열
     * @return 16진수 문자열이면 true
     */
    private boolean isHex(final String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            final boolean digit = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
            if (!digit) {
                return false;
            }
        }
        return true;
    }
}
