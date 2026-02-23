package com.nori.tc.comm.gateway.socket.socketType.types.template;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.gateway.socket.frame.SocketFrame;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 신규 socketType 구현을 빠르게 시작하기 위한 예제 템플릿 핸들러입니다.
 *
 * <p>이 파일은 {@code src/main/java}가 아닌 {@code examples} 경로에 위치하는 예제 코드이며,
 * 실제 프로젝트에 적용할 때는 팀 규칙에 맞게 클래스명/패키지명/로깅 정책을 조정해서 복사해 사용합니다.</p>
 *
 * <p>예제 목적상 프레임 추출, 디코드, 인코드를 한 파일에 모아두었으며,
 * 실제 장비 규격이 복잡해지면 extractor/decoder/encoder로 분리하는 것을 권장합니다.</p>
 */
public final class TemplateSocketTypeHandler implements SocketTypeHandler {

    /**
     * 예제 핸들러 동작 로그를 출력하기 위한 로거입니다.
     *
     * <p>예제 코드에서도 생성 시점과 파싱 시점의 최소 로그 위치를 보여주기 위해
     * {@code info}/{@code debug} 레벨 로그를 함께 사용합니다.</p>
     */
    private static final Logger log = LoggerFactory.getLogger(TemplateSocketTypeHandler.class);

    /**
     * 예제 socketType 값입니다.
     *
     * <p>실사용 전에는 반드시 DB 컬럼 {@code tc_eqp_socket.socket_protocol_type}에 저장되는
     * 실제 값으로 교체해야 합니다.</p>
     */
    public static final String SOCKET_TYPE = "TEMPLATE_SOCKET_TYPE";

    /**
     * 디코드/인코드에 사용하는 문자셋입니다.
     *
     * <p>장비 규격이 ASCII 또는 제조사 전용 문자셋을 요구할 경우 이 값을 변경합니다.</p>
     */
    private final Charset charset;

    /**
     * 기본 문자셋(UTF-8)을 사용하는 템플릿 핸들러를 생성합니다.
     */
    public TemplateSocketTypeHandler() {
        this(StandardCharsets.UTF_8);
    }

    /**
     * 지정된 문자셋을 사용하는 템플릿 핸들러를 생성합니다.
     *
     * @param charset 디코드/인코드 로직에 사용할 문자셋 (null이면 UTF-8로 대체)
     */
    public TemplateSocketTypeHandler(final Charset charset) {
        this.charset = (charset == null) ? StandardCharsets.UTF_8 : charset;
        log.info("TemplateSocketTypeHandler 예제 핸들러 초기화. socketType={}, charset={}",
                SOCKET_TYPE,
                this.charset.name());
    }

    /**
     * 이 핸들러가 처리하는 socketType 식별자를 반환합니다.
     *
     * @return socketType 문자열 값
     */
    @Override
    public String socketType() {
        return SOCKET_TYPE;
    }

    /**
     * 누적 버퍼에서 프레임 1건을 추출합니다.
     *
     * <p>예제 기본 규칙:</p>
     * <p>1) 줄바꿈 문자 LF({@code '\n'})를 프레임 종료 구분자로 사용합니다.</p>
     * <p>2) 완전한 프레임이 아직 도착하지 않았으면 {@code null}을 반환합니다.</p>
     * <p>3) 프레임 길이가 {@code maxFrameBytes}를 초과하면 예외를 발생시켜 상위 계층에서 방어합니다.</p>
     *
     * @param buffer 재조립 버퍼(누적 수신 데이터 저장소)
     * @param maxFrameBytes 프레임 최대 허용 크기(안전 제한)
     * @return 추출된 프레임, 아직 미완성이면 null
     */
    @Override
    public SocketFrame tryExtractOne(final ReassemblyBuffer buffer, final int maxFrameBytes) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer is null");
        }

        final int readable = buffer.readableBytes();
        if (readable == 0) {
            if (log.isDebugEnabled()) {
                log.debug("Template 프레임 추출 스킵. 이유=readableBytes가 0");
            }
            return null;
        }

        int delimiterIndex = -1;
        for (int i = 0; i < readable; i++) {
            if (buffer.get(i) == (byte) '\n') {
                delimiterIndex = i;
                break;
            }
        }

        if (delimiterIndex < 0) {
            if (log.isDebugEnabled()) {
                log.debug("Template 프레임 추출 보류. 이유=LF 구분자 미도착, readableBytes={}", readable);
            }
            return null;
        }

        final int frameLength = delimiterIndex + 1;
        if (frameLength > maxFrameBytes) {
            throw new IllegalStateException("Socket frame too large: " + frameLength + " > " + maxFrameBytes);
        }

        final byte[] frameBytes = buffer.copy(0, frameLength);
        buffer.discard(frameLength);

        if (log.isDebugEnabled()) {
            log.debug("Template 프레임 추출 완료. frameLength={}, remainingReadableBytes={}",
                    frameLength,
                    buffer.readableBytes());
        }
        return new SocketFrame(frameBytes, System.currentTimeMillis());
    }

    /**
     * 추출된 프레임 바이트를 표준 디코드 결과 모델로 변환합니다.
     *
     * <p>예제 기본 규칙:</p>
     * <p>1) 프레임 문자열의 첫 번째 토큰을 메시지명으로 사용합니다.</p>
     * <p>2) 나머지 문자열을 본문(body)으로 사용합니다.</p>
     * <p>3) 개행 문자는 제거하고 trim 처리합니다.</p>
     *
     * @param frameBytes 추출된 프레임 바이트 배열
     * @return 표준화된 디코드 결과
     */
    @Override
    public SocketTypeDecodeResult decode(final byte[] frameBytes) {
        if (frameBytes == null) {
            throw new IllegalArgumentException("frameBytes is null");
        }

        final String text = new String(frameBytes, charset)
                .replace("\r", "")
                .replace("\n", "")
                .trim();

        if (text.isBlank()) {
            throw new IllegalArgumentException("Empty template frame");
        }

        final String[] parts = text.split("\\s+", 2);
        final String messageName = parts[0];
        final String bodyText = (parts.length > 1) ? parts[1] : "";

        if (log.isDebugEnabled()) {
            log.debug("Template 프레임 디코드 완료. messageName={}, bodyLength={}, rawLength={}",
                    messageName,
                    bodyText.length(),
                    frameBytes.length);
        }

        return new SocketTypeDecodeResult(
                messageName,
                Map.of("rawText", text),
                bodyText
        );
    }

    /**
     * 아웃바운드 커맨드 객체를 전송 바이트로 인코드합니다.
     *
     * <p>예제 기본 규칙:</p>
     * <p>1) {@code command.toString()} 결과를 그대로 사용합니다.</p>
     * <p>2) 구분자(LF/CRLF 등)는 자동으로 추가/제거하지 않습니다.</p>
     * <p>3) 실제 장비 규격에 맞게 checksum, header, delimiter를 구현해야 합니다.</p>
     *
     * @param command 아웃바운드 커맨드 객체
     * @return 인코드 결과(전송 바이트 + 설명 문자열)
     */
    @Override
    public SocketTypeEncodeResult encode(final Object command) {
        if (command == null) {
            throw new IllegalArgumentException("command is null");
        }

        final byte[] bytes = command.toString().getBytes(charset);
        if (log.isDebugEnabled()) {
            log.debug("Template 아웃바운드 인코드 완료. commandType={}, byteLength={}",
                    command.getClass().getName(),
                    bytes.length);
        }
        return new SocketTypeEncodeResult(bytes, "template outbound pass-through encoding");
    }
}
