package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.gateway.config.props.GatewayNettyProperties;
import com.nori.tc.comm.gateway.config.props.GatewaySocketProperties;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.comm.gateway.action.SocketFrame;
import com.nori.tc.comm.gateway.action.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.action.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.action.SocketTypeHandler;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * SOCKET 채널의 UNBOUND 구간에서 `INITIALIZE_REP` 프레임을 파싱하여 eqpId를 추출하는 컴포넌트입니다.
 *
 * <p>중요한 설계 포인트는 다음과 같습니다.</p>
 * <p>1) 설비별로 `socketType`(= `tc_eqp_socket.socket_protocol_type`)가 다를 수 있습니다.</p>
 * <p>2) 따라서 initialize 응답 프레이밍/디코드 또한 채널에 확정된 socketType 핸들러로 수행해야 합니다.</p>
 * <p>3) `default-socket-type`은 socketType 미지정(null/blank) 상황에서만 fallback으로 사용합니다.</p>
 */
public final class SocketEqpIdExtractor implements EqpIdExtractor {

    private static final Logger log = LoggerFactory.getLogger(SocketEqpIdExtractor.class);

    /**
     * SOCKET 공통 설정입니다.
     *
     * <p>최대 프레임 크기, 기본 socketType fallback 값 등을 조회할 때 사용합니다.</p>
     */
    private final GatewaySocketProperties socketProperties;

    /**
     * Netty 초기화 관련 설정입니다.
     *
     * <p>initialize 명령 문자열, initialize reply prefix, eqpId key 등 프로토콜 공통 토큰을 조회합니다.</p>
     */
    private final GatewayNettyProperties nettyProperties;

    /**
     * socketType -> 핸들러 레지스트리입니다.
     *
     * <p>설비별 socketType에 맞는 프레이밍/디코드/인코드 구현체를 조회합니다.</p>
     */
    private final SocketTypeRegistry socketTypeRegistry;

    /**
     * SOCKET 초기 바인딩 파서 컴포넌트를 생성합니다.
     *
     * @param socketProperties SOCKET 공통 설정
     * @param nettyProperties Netty initialize 관련 설정
     * @param socketTypeRegistry socketType 핸들러 레지스트리
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
     * 기본 socketType fallback을 사용하여 `INITIALIZE_REP`에서 eqpId를 추출합니다.
     *
     * <p>이 메서드는 {@link EqpIdExtractor} 호환성을 위한 기본 경로이며,
     * 실제 운영 경로에서는 채널/리스너에서 확정한 socketType을 넘기는 오버로드 사용이 권장됩니다.</p>
     *
     * @param buffer UNBOUND 누적 버퍼
     * @return eqpId 추출 성공 시 Optional.of(eqpId), 아니면 Optional.empty()
     */
    @Override
    public Optional<String> tryExtractEqpId(final ReassemblyBuffer buffer) {
        final String defaultSocketType = socketProperties.getDefaultSocketType();
        if (log.isDebugEnabled()) {
            log.debug("SOCKET INITIALIZE_REP 파싱에 기본 socketType fallback 사용. socketType={}", defaultSocketType);
        }
        return tryExtractEqpId(buffer, defaultSocketType);
    }

    /**
     * 채널/리스너에 확정된 socketType 핸들러를 사용하여 `INITIALIZE_REP`에서 eqpId를 추출합니다.
     *
     * <p>UNBOUND 상태에서도 설비별 프레이밍 규칙이 적용되므로, 이 메서드는
     * `tryExtractOne()` / `decode()` 모두 동일 socketType 핸들러를 일관되게 사용합니다.</p>
     *
     * @param buffer UNBOUND 누적 버퍼
     * @param socketType 채널에 확정된 socketType (null/blank이면 기본값 fallback)
     * @return eqpId 추출 성공 시 Optional.of(eqpId), 아니면 Optional.empty()
     */
    public Optional<String> tryExtractEqpId(
            final ReassemblyBuffer buffer,
            final String socketType
    ) {
        final String resolvedSocketType = resolveSocketTypeOrDefault(socketType);
        final SocketTypeHandler handler = socketTypeRegistry.getRequired(resolvedSocketType);
        final String expectedInitializeReply = nettyProperties.getSocketInitializeReplyPrefix();

        while (true) {
            final SocketFrame frame = handler.tryExtractOne(buffer, socketProperties.getMaxFrameBytes());
            if (frame == null) {
                return Optional.empty();
            }

            final SocketTypeDecodeResult decoded;
            try {
                decoded = handler.decode(frame.bytes());
            } catch (Exception ex) {
                // initialize 응답 규약이 아닌 프레임은 UNBOUND 단계에서 버리고 다음 프레임을 확인합니다.
                if (log.isDebugEnabled()) {
                    log.debug("SOCKET UNBOUND 프레임 decode 실패(무시 후 계속 탐색). socketType={}", resolvedSocketType, ex);
                }
                continue;
            }

            final String messageName = decoded.messageName();
            if (!messageName.equalsIgnoreCase(expectedInitializeReply)) {
                // initialize reply가 아니면 바인딩용 프레임이 아니므로 다음 프레임을 계속 확인합니다.
                if (log.isDebugEnabled()) {
                    log.debug("SOCKET UNBOUND 프레임이 initialize reply가 아닙니다. messageName={}, expected={}, socketType={}",
                            messageName,
                            expectedInitializeReply,
                            resolvedSocketType);
                }
                continue;
            }

            // socketType 구현마다 rawLine/rawText/body 저장 방식이 다를 수 있으므로 우선순위로 후보 문자열을 구성합니다.
            final String rawLine = decoded.attributes().getOrDefault("rawLine", "");
            final String rawText = decoded.attributes().getOrDefault("rawText", "");
            final String body = decoded.body() == null ? "" : decoded.body().toString();
            final String candidate = !rawLine.isBlank()
                    ? rawLine
                    : (!rawText.isBlank() ? rawText : body);

            final Optional<String> eqpId = parseEqpId(candidate, nettyProperties.getSocketEqpIdKey());
            if (eqpId.isPresent()) {
                if (log.isDebugEnabled()) {
                    try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId.get())) {
                        log.debug("SOCKET INITIALIZE_REP에서 eqpId 추출 성공. eqpId={}, socketType={}",
                                eqpId.get(),
                                resolvedSocketType);
                    }
                }
                return eqpId;
            }

            if (log.isDebugEnabled()) {
                log.debug("SOCKET INITIALIZE_REP에 eqpId 토큰이 없습니다. key={}, socketType={}",
                        nettyProperties.getSocketEqpIdKey(),
                        resolvedSocketType);
            }
            // initialize reply 형식이지만 eqpId 토큰이 없으면 바인딩에 사용할 수 없으므로 다음 프레임을 계속 탐색합니다.
        }
    }

    /**
     * 문자열에서 `key=value` 형태의 eqpId 토큰을 파싱합니다.
     *
     * <p>예: `CMD=INITIALIZE_REP EQPID=TEST001` -> `TEST001`</p>
     *
     * @param text initialize 응답 원문/본문 후보 문자열
     * @param key eqpId 키 이름 (예: `EQPID`)
     * @return 파싱 성공 시 Optional.of(eqpId), 실패 시 Optional.empty()
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
     * 기본 socketType fallback을 사용하여 initialize 명령을 wire bytes로 인코딩합니다.
     *
     * <p>호환성 메서드이며, 실제 운영 경로에서는 채널/리스너 확정 socketType을 넘기는 오버로드 사용을 권장합니다.</p>
     *
     * @return socketType 핸들러로 인코딩된 initialize 명령 bytes
     */
    public byte[] initializeCommandBytes() {
        final String defaultSocketType = socketProperties.getDefaultSocketType();
        if (log.isDebugEnabled()) {
            log.debug("SOCKET initialize 인코딩에 기본 socketType fallback 사용. socketType={}", defaultSocketType);
        }
        return initializeCommandBytes(defaultSocketType);
    }

    /**
     * 채널/리스너에 확정된 socketType 핸들러를 사용하여 initialize 명령을 인코딩합니다.
     *
     * <p>initialize 메시지도 설비별 프로토콜의 일부이므로, 일반 outbound 메시지와 동일하게
     * socketType 핸들러의 {@link SocketTypeHandler#encode(Object)} 경로를 사용합니다.</p>
     *
     * @param socketType 채널에 확정된 socketType (null/blank이면 기본값 fallback)
     * @return socketType 핸들러가 생성한 initialize 명령 bytes
     */
    public byte[] initializeCommandBytes(final String socketType) {
        final String resolvedSocketType = resolveSocketTypeOrDefault(socketType);
        final SocketTypeHandler handler = socketTypeRegistry.getRequired(resolvedSocketType);
        final SocketTypeEncodeResult encoded = handler.encode(nettyProperties.getSocketInitializeCommand());

        if (encoded.bytes().length == 0) {
            throw new IllegalStateException("Encoded SOCKET initialize command is empty. socketType=" + resolvedSocketType);
        }

        if (log.isDebugEnabled()) {
            log.debug("SOCKET initialize 명령 인코딩 완료. socketType={}, description={}, bytes={}",
                    resolvedSocketType,
                    encoded.description(),
                    encoded.bytes().length);
        }
        return encoded.bytes();
    }

    /**
     * socketType 문자열을 정규화하고, 비어 있으면 설정 기본값으로 보정합니다.
     *
     * <p>정상 경로에서는 설비/리스너 기준 socketType이 전달되어야 하며,
     * 여기의 fallback은 null/blank 방어 및 하위 호환성 유지를 위한 안전장치입니다.</p>
     *
     * @param socketType 외부 입력 socketType
     * @return 정규화된 socketType (blank 아님)
     */
    private String resolveSocketTypeOrDefault(final String socketType) {
        final String normalized = normalizeText(socketType);
        if (normalized != null) {
            return normalized;
        }

        final String fallback = normalizeText(socketProperties.getDefaultSocketType());
        if (fallback == null) {
            throw new IllegalStateException("Default SOCKET socketType is not configured");
        }
        return fallback;
    }

    /**
     * 문자열 정규화 유틸리티입니다.
     *
     * @param text 입력 문자열
     * @return trim 결과가 비어 있지 않으면 해당 문자열, 아니면 null
     */
    private String normalizeText(final String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
