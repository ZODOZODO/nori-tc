package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.config.GatewayNettyProperties;
import com.nori.tc.comm.gateway.config.GatewaySocketProperties;
import com.nori.tc.comm.gateway.socket.frame.SocketFrame;
import com.nori.tc.comm.gateway.socket.socketType.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.socket.socketType.SocketTypeHandler;
import com.nori.tc.comm.gateway.socket.socketType.SocketTypeRegistry;
import com.nori.tc.comm.core.buffer.ReassemblyBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * SOCKET 초기화 응답에서 eqpId 추출.
 *
 * 예시:
 * - Gateway -> "CMD=INITIALIZE"
 * - Client -> "CMD=INITIALIZE_REP EQPID=TEST001"
 */
public final class SocketEqpIdExtractor implements EqpIdExtractor {

    private static final Logger log = LoggerFactory.getLogger(SocketEqpIdExtractor.class);

    private final GatewaySocketProperties socketProperties;
    private final GatewayNettyProperties nettyProperties;
    private final SocketTypeRegistry socketTypeRegistry;

    
    /**
     * 게이트웨이 Netty 어댑터 구성 요소를 초기화합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param socketProperties 통신 채널/세션 정보
     * @param nettyProperties 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param socketTypeRegistry 통신 채널/세션 정보
     */
    public SocketEqpIdExtractor(
            final GatewaySocketProperties socketProperties,
            final GatewayNettyProperties nettyProperties,
            final SocketTypeRegistry socketTypeRegistry
    ) {
        this.socketProperties = socketProperties;
        this.nettyProperties = nettyProperties;
        this.socketTypeRegistry = socketTypeRegistry;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param buffer 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @return 조회 결과(Optional)
     */
    @Override
    public Optional<String> tryExtractEqpId(final ReassemblyBuffer buffer) {
        final String socketType = socketProperties.getDefaultSocketType();
        final SocketTypeHandler handler = socketTypeRegistry.getRequired(socketType);
        final String expected = nettyProperties.getSocketInitializeReplyPrefix();

        while (true) {
            final SocketFrame frame = handler.tryExtractOne(buffer, socketProperties.getMaxFrameBytes());
            if (frame == null) {
                return Optional.empty();
            }

            final SocketTypeDecodeResult decoded;
            try {
                decoded = handler.decode(frame.bytes());
            } catch (Exception ex) {
                // 디코딩 실패 프레임은 드롭하고 다음 프레임을 계속 탐색합니다.
                continue;
            }

            final String messageName = decoded.messageName();
            if (!messageName.equalsIgnoreCase(expected)) {
                // 등록 메시지가 아니면 드롭 (UNBOUND 단계 처리 금지)
                continue;
            }

            // body 또는 rawLine에서 EQPID 추출
            final String rawLine = decoded.attributes().getOrDefault("rawLine", "");
            final String body = decoded.body() == null ? "" : decoded.body().toString();
            final String candidate = !rawLine.isBlank() ? rawLine : body;

            final Optional<String> eqpId = parseEqpId(candidate, nettyProperties.getSocketEqpIdKey());
            if (eqpId.isPresent()) {
                if (log.isDebugEnabled()) {
                    log.debug("SOCKET eqpId extracted. eqpId={}", eqpId.get());
                }
                return eqpId;
            }
            // EQPID가 없으면 해당 프레임은 드롭하고 다음 프레임을 계속 탐색합니다.
        }
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param text 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param key 대상 키 값
     * @return 조회 결과(Optional)
     */
    private Optional<String> parseEqpId(final String text, final String key) {
        // 파싱 단계: 입력 포맷을 해석해 필요한 필드만 안전하게 추출합니다.
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        final String upper = text.toUpperCase(Locale.ROOT);
        final String keyToken = key.toUpperCase(Locale.ROOT) + "=";
        final int idx = upper.indexOf(keyToken);
        if (idx < 0) {
            return Optional.empty();
        }

        final String tail = text.substring(idx + keyToken.length()).trim();
        final String eqpId = tail.split("\\s+")[0];
        if (eqpId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(eqpId);
    }

    
    /**
     * 게이트웨이 Netty 어댑터 실행 환경을 초기화하고 기동합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    public byte[] initializeCommandBytes() {
        return nettyProperties.getSocketInitializeCommand().getBytes(StandardCharsets.UTF_8);
    }
}
