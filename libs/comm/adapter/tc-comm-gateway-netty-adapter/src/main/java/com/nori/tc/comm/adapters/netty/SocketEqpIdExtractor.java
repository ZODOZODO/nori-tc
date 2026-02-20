package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.config.GatewayNettyProperties;
import com.nori.tc.comm.gateway.config.GatewaySocketProperties;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.comm.gateway.socket.frame.SocketFrame;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;
import com.nori.tc.comm.core.buffer.ReassemblyBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;

/**
 * SOCKET `INITIALIZE_REP` 프레임에서 eqpId를 추출하는 파서입니다.
 *
 * <p>UNBOUND 단계에서 수신되는 프레임 중 초기화 응답만 선별하여
 * `EQPID=<value>` 키를 파싱합니다.</p>
 *
 * <p>예시:</p>
 * <p>- Gateway -> `CMD=INITIALIZE`</p>
 * <p>- Client -> `CMD=INITIALIZE_REP EQPID=TEST001`</p>
 */
public final class SocketEqpIdExtractor implements EqpIdExtractor {

    private static final Logger log = LoggerFactory.getLogger(SocketEqpIdExtractor.class);

    private final GatewaySocketProperties socketProperties;
    private final GatewayNettyProperties nettyProperties;
    private final SocketTypeRegistry socketTypeRegistry;

    /**
     * SOCKET eqpId 추출기를 초기화합니다.
     *
     * @param socketProperties SOCKET 프레임 추출/제한 설정
     * @param nettyProperties 초기화 응답 prefix, eqpId key 설정
     * @param socketTypeRegistry socket type별 프레임 핸들러 레지스트리
     */
    public SocketEqpIdExtractor(
            final GatewaySocketProperties socketProperties,
            final GatewayNettyProperties nettyProperties,
            final SocketTypeRegistry socketTypeRegistry
    ) {
        this.socketProperties = Objects.requireNonNull(socketProperties, "socketProperties is null");
        this.nettyProperties = Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.socketTypeRegistry = Objects.requireNonNull(socketTypeRegistry, "socketTypeRegistry is null");
    }

    /**
     * 버퍼에 누적된 SOCKET 프레임에서 eqpId를 추출합니다.
     *
     * <p>동작 규칙:</p>
     * <p>1) 프레임 디코딩 실패 프레임은 드롭하고 다음 프레임을 검사합니다.</p>
     * <p>2) `socketInitializeReplyPrefix`와 messageName이 일치하는 프레임만 대상으로 삼습니다.</p>
     * <p>3) `socketEqpIdKey` 기준으로 eqpId를 추출하면 즉시 반환합니다.</p>
     *
     * @param buffer UNBOUND 누적 버퍼
     * @return eqpId가 추출되면 Optional.of(eqpId), 아니면 Optional.empty()
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
                // 형식이 맞지 않는 프레임은 바인딩 대상이 아니므로 버리고 다음 프레임을 확인합니다.
                continue;
            }

            final String messageName = decoded.messageName();
            if (!messageName.equalsIgnoreCase(expected)) {
                // 초기화 응답이 아니면 UNBOUND 단계에서 처리하지 않고 버립니다.
                continue;
            }

            // socket type 구현에 따라 rawLine/body 중 값이 존재하는 쪽을 사용합니다.
            final String rawLine = decoded.attributes().getOrDefault("rawLine", "");
            final String body = decoded.body() == null ? "" : decoded.body().toString();
            final String candidate = !rawLine.isBlank() ? rawLine : body;

            final Optional<String> eqpId = parseEqpId(candidate, nettyProperties.getSocketEqpIdKey());
            if (eqpId.isPresent()) {
                if (log.isDebugEnabled()) {
                    try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId.get())) {
                        log.debug("SOCKET eqpId extracted from INITIALIZE_REP. eqpId={}", eqpId.get());
                    }
                }
                return eqpId;
            }
            if (log.isDebugEnabled()) {
                log.debug("SOCKET INITIALIZE_REP does not contain eqpId token. key={}",
                        nettyProperties.getSocketEqpIdKey());
            }
            // 키가 없으면 해당 프레임은 바인딩에 사용할 수 없으므로 버리고 다음 프레임을 탐색합니다.
        }
    }

    /**
     * 문자열에서 `key=value` 형태의 eqpId를 파싱합니다.
     *
     * @param text 파싱 대상 원문
     * @param key eqpId 키(예: `EQPID`)
     * @return 파싱 성공 시 eqpId
     */
    private Optional<String> parseEqpId(final String text, final String key) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        if (key == null || key.isBlank()) {
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
     * 게이트웨이가 송신할 SOCKET initialize 명령 바이트를 생성합니다.
     *
     * @return UTF-8 인코딩된 initialize 명령 바이트
     */
    public byte[] initializeCommandBytes() {
        return nettyProperties.getSocketInitializeCommand().getBytes(StandardCharsets.UTF_8);
    }
}
