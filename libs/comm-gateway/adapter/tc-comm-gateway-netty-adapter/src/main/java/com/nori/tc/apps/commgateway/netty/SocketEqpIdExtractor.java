package com.nori.tc.apps.commgateway.netty;

import com.nori.tc.apps.commgateway.config.GatewayNettyProperties;
import com.nori.tc.apps.commgateway.config.GatewaySocketProperties;
import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.socket.frame.SocketFrame;
import com.nori.tc.comm.socket.socketType.SocketTypeDecodeResult;
import com.nori.tc.comm.socket.socketType.SocketTypeHandler;
import com.nori.tc.comm.socket.socketType.SocketTypeRegistry;

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

    private final GatewaySocketProperties socketProperties;
    private final GatewayNettyProperties nettyProperties;
    private final SocketTypeRegistry socketTypeRegistry;

    public SocketEqpIdExtractor(
            final GatewaySocketProperties socketProperties,
            final GatewayNettyProperties nettyProperties,
            final SocketTypeRegistry socketTypeRegistry
    ) {
        this.socketProperties = socketProperties;
        this.nettyProperties = nettyProperties;
        this.socketTypeRegistry = socketTypeRegistry;
    }

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
                return eqpId;
            }
            // EQPID가 없으면 해당 프레임은 드롭하고 다음 프레임을 계속 탐색합니다.
        }
    }

    private Optional<String> parseEqpId(final String text, final String key) {
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

    public byte[] initializeCommandBytes() {
        return nettyProperties.getSocketInitializeCommand().getBytes(StandardCharsets.UTF_8);
    }
}
